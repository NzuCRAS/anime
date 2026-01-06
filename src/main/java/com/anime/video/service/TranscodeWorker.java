package com.anime.video.service;

import com.anime.common.entity.video.Video;
import com.anime.common.entity.video.VideoTranscode;
import com.anime.common.mapper.video.VideoMapper;
import com.anime.common.mapper.video.VideoTranscodeMapper;
import com.anime.common.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * TranscodeWorker（video-level）：一次性为一个 videoId 生成多码率 HLS（master + variants）
 *
 * 改动点：
 * - 在构造 ffmpeg 命令前探测是否存在音频流（hasAudio），若无音频则不添加 audio map/options
 * - 更稳健地收集输出文件并上传
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscodeWorker {

    private final VideoTranscodeMapper transcodeMapper;
    private final VideoMapper videoMapper;
    private final AttachmentService attachmentService;
    private final S3Client s3Client;

    @Value("${storage.bucket}")
    private String bucket;

    private static final Pattern VARIANT_INDEX_PATTERN = Pattern.compile(".*?(?:_|-)(\\d+)\\.m3u8$");

    @Async("transcodeExecutor")
    public void processTranscode(Long videoId) {
        log.info("Dispatch transcode job for videoId={}", videoId);
        List<VideoTranscode> transList = transcodeMapper.listByVideoId(videoId);
        if (transList == null || transList.isEmpty()) {
            log.warn("no transcode rows for video {}", videoId);
            return;
        }

        try {
            Video video = videoMapper.selectById(videoId);
            if (video == null) {
                log.warn("video not found {}", videoId);
                return;
            }
            Long sourceAttId = video.getSourceAttachmentId();
            if (sourceAttId == null) {
                log.warn("video {} has no sourceAttachmentId", videoId);
                return;
            }

            // 1) download source to tmp
            String tmpDir = System.getProperty("java.io.tmpdir");
            Path srcPath = Path.of(tmpDir, "src-" + UUID.randomUUID() + ".mp4");
            String presigned = attachmentService.generatePresignedGetUrl(sourceAttId, 60 * 30); // 30min
            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(presigned)).GET().build();
            var resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofFile(srcPath));
            if (resp.statusCode() >= 400) {
                throw new IllegalStateException("download source failed: " + resp.statusCode());
            }

            // 2) probe source resolution and detect audio presence via ffprobe
            int sourceWidth = 0, sourceHeight = 0;
            boolean hasAudio = false;
            try {
                // probe resolution
                ProcessBuilder pbProbeRes = new ProcessBuilder(
                        "ffprobe", "-v", "error",
                        "-select_streams", "v:0",
                        "-show_entries", "stream=width,height",
                        "-of", "csv=p=0:s=x",
                        srcPath.toString()
                );
                pbProbeRes.redirectErrorStream(true);
                Process pProbeRes = pbProbeRes.start();
                boolean ok = pProbeRes.waitFor(10, TimeUnit.SECONDS);
                if (!ok) {
                    pProbeRes.destroyForcibly();
                    throw new RuntimeException("ffprobe timeout for resolution");
                }
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pProbeRes.getInputStream()))) {
                    String line = r.readLine();
                    if (line != null && line.contains("x")) {
                        String[] sp = line.trim().split("x");
                        sourceWidth = Integer.parseInt(sp[0]);
                        sourceHeight = Integer.parseInt(sp[1]);
                    }
                }

                // probe audio existence
                ProcessBuilder pbProbeAudio = new ProcessBuilder(
                        "ffprobe", "-v", "error",
                        "-select_streams", "a",
                        "-show_entries", "stream=index",
                        "-of", "csv=p=0",
                        srcPath.toString()
                );
                pbProbeAudio.redirectErrorStream(true);
                Process pProbeAudio = pbProbeAudio.start();
                boolean ok2 = pProbeAudio.waitFor(5, TimeUnit.SECONDS);
                if (ok2) {
                    try (BufferedReader r2 = new BufferedReader(new InputStreamReader(pProbeAudio.getInputStream()))) {
                        String line2 = r2.readLine();
                        if (line2 != null && !line2.trim().isEmpty()) {
                            hasAudio = true;
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("ffprobe failed, assume audio present and resolution 1920x1080. ex={}", ex.getMessage());
                sourceWidth = 1920;
                sourceHeight = 1080;
                hasAudio = true;
            }

            log.info("Source resolution detected: {}x{} , hasAudio={}", sourceWidth, sourceHeight, hasAudio);

            // 3) prepare profiles and filter by source height (no upscaling)
            List<VideoTranscode> profiles = transList.stream()
                    .sorted(Comparator.comparingInt(this::profileOrder))
                    .toList();

            List<VideoTranscode> toProduce = new ArrayList<>();
            for (VideoTranscode tv : profiles) {
                String res = tv.getResolution();
                if (res == null || !res.contains("x")) {
                    toProduce.add(tv);
                    continue;
                }
                String[] wh = res.split("x");
                int h = Integer.parseInt(wh[1]);
                if (h <= sourceHeight) {
                    toProduce.add(tv);
                } else {
                    log.info("Skip producing {} for video {} because source height {} < target {}", tv.getRepresentationId(), videoId, sourceHeight, h);
                    tv.setStatus("failed");
                    transcodeMapper.updateById(tv);
                }
            }

            if (toProduce.isEmpty()) {
                log.warn("no producible profiles for video {}", videoId);
                video.setStatus("ready");
                videoMapper.updateById(video);
                try { Files.deleteIfExists(srcPath); } catch (Exception ignore) {}
                return;
            }

            // 4) create dedicated outDir
            Path outDir = Path.of(tmpDir, "hls-" + videoId + "-" + UUID.randomUUID());
            Files.createDirectories(outDir);

            // 5) build ffmpeg command
            int n = toProduce.size();

            StringBuilder fc = new StringBuilder();
            fc.append("[0:v]split=").append(n);
            for (int i = 0; i < n; i++) fc.append("[v").append(i).append("]");
            fc.append(";");
            for (int i = 0; i < n; i++) {
                String resolution = toProduce.get(i).getResolution();
                if (resolution == null || resolution.isBlank()) resolution = "1280x720";
                fc.append("[v").append(i).append("]scale=").append(resolution).append("[v").append(i).append("out];");
            }

            List<String> ffArgs = new ArrayList<>();
            ffArgs.add("ffmpeg");
            ffArgs.add("-y");
            ffArgs.add("-i");
            ffArgs.add(srcPath.toString());
            ffArgs.add("-filter_complex");
            ffArgs.add(fc.toString());

            // build mapping and codec args. For video streams we map [v{i}out].
            for (int i = 0; i < n; i++) {
                ffArgs.add("-map");
                ffArgs.add("[v" + i + "out]");
                if (hasAudio) {
                    ffArgs.add("-map");
                    ffArgs.add("0:a");
                }
                // video codec per stream
                ffArgs.add("-c:v:" + i);
                ffArgs.add("libx264");
                ffArgs.add("-b:v:" + i);
                ffArgs.add(String.valueOf(toProduce.get(i).getBitrate()));
                // audio codec per stream only if audio exists
                if (hasAudio) {
                    ffArgs.add("-c:a:" + i);
                    ffArgs.add("aac");
                    ffArgs.add("-b:a:" + i);
                    ffArgs.add("128k");
                }
            }

            // HLS options
            ffArgs.add("-f");
            ffArgs.add("hls");
            ffArgs.add("-hls_time");
            ffArgs.add("6");
            ffArgs.add("-hls_playlist_type");
            ffArgs.add("vod");
            ffArgs.add("-master_pl_name");
            ffArgs.add("master.m3u8");

            // var_stream_map: include audio mapping only when audio exists
            StringBuilder vmap = new StringBuilder();
            for (int i = 0; i < n; i++) {
                if (i > 0) vmap.append(" ");
                vmap.append("v:").append(i);
                if (hasAudio) vmap.append(",a:").append(i);
            }
            ffArgs.add("-var_stream_map");
            ffArgs.add(vmap.toString());

            // output pattern to outDir
            String tmpOutPattern = outDir.resolve("stream_%v.m3u8").toString();
            ffArgs.add(tmpOutPattern);

            log.info("Running ffmpeg for video {} with args: {}", videoId, String.join(" ", ffArgs));

            ProcessBuilder pb = new ProcessBuilder(ffArgs);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            // capture ffmpeg output and log
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    log.debug("[ffmpeg] {}", line);
                }
            }

            boolean finished = p.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                p.destroyForcibly();
                throw new RuntimeException("ffmpeg timeout");
            }
            int exit = p.exitValue();
            if (exit != 0) {
                throw new RuntimeException("ffmpeg exited with code " + exit);
            }

            // 6) collect generated files from outDir
            List<Path> generatedFiles;
            try (Stream<Path> s = Files.walk(outDir)) {
                generatedFiles = s.filter(pth -> {
                    String fn = pth.getFileName().toString().toLowerCase();
                    return fn.endsWith(".m3u8") || fn.endsWith(".ts") || fn.endsWith(".mp4");
                }).toList();
            }

            if (generatedFiles.isEmpty()) {
                throw new RuntimeException("ffmpeg did not produce any expected output files");
            }

            // 7) upload to S3
            String basePrefix = String.format("videos/%d/hls", videoId);
            for (Path file : generatedFiles) {
                String key = basePrefix + "/" + file.getFileName().toString();
                uploadToS3(file, key, guessContentType(file));
            }

            // 8) map variant playlists to transcode rows
            Map<Integer, Path> indexToPlaylist = new HashMap<>();
            for (Path pth : generatedFiles) {
                String fn = pth.getFileName().toString();
                if (fn.toLowerCase().endsWith(".m3u8") && fn.contains("stream_")) {
                    Matcher m = VARIANT_INDEX_PATTERN.matcher(fn);
                    if (m.find()) {
                        try {
                            int idx = Integer.parseInt(m.group(1));
                            indexToPlaylist.put(idx, pth);
                        } catch (NumberFormatException ignore) {}
                    }
                }
            }

            for (int i = 0; i < toProduce.size(); i++) {
                VideoTranscode vt = toProduce.get(i);
                Path vp = indexToPlaylist.get(i);
                if (vp != null) {
                    String key = basePrefix + "/" + vp.getFileName().toString();
                    vt.setManifestPath(key);
                    vt.setStatus("ready");
                } else {
                    // fallback: pick any non-master m3u8
                    Optional<Path> maybe = generatedFiles.stream()
                            .filter(pth -> pth.getFileName().toString().toLowerCase().endsWith(".m3u8"))
                            .filter(pth -> !pth.getFileName().toString().equalsIgnoreCase("master.m3u8"))
                            .findFirst();
                    if (maybe.isPresent()) {
                        String key = basePrefix + "/" + maybe.get().getFileName().toString();
                        vt.setManifestPath(key);
                        vt.setStatus("ready");
                    } else {
                        vt.setStatus("failed");
                    }
                }
                transcodeMapper.updateById(vt);
            }

            // 9) mark video ready if any transcode ready
            var updatedList = transcodeMapper.listByVideoId(videoId);
            boolean anyReady = updatedList.stream().anyMatch(x -> "ready".equalsIgnoreCase(x.getStatus()));
            if (anyReady) {
                video.setStatus("ready");
                videoMapper.updateById(video);
            }

            // cleanup temp files (keep for debug if you want)
            try {
                Files.deleteIfExists(srcPath);
                try (Stream<Path> s = Files.walk(outDir)) {
                    s.sorted(Comparator.reverseOrder()).forEach(pth -> {
                        try { Files.deleteIfExists(pth); } catch (Exception ignore) {}
                    });
                }
            } catch (Exception ignore) {}

            log.info("Transcode for video {} finished", videoId);

        } catch (Exception ex) {
            log.error("processTranscode failed for videoId {}: {}", videoId, ex.getMessage(), ex);
            // mark related transcodes failed
            try {
                var list = transcodeMapper.listByVideoId(videoId);
                for (VideoTranscode t : list) {
                    t.setStatus("failed");
                    transcodeMapper.updateById(t);
                }
                var vid = videoMapper.selectById(videoId);
                if (vid != null) {
                    vid.setStatus("failed");
                    videoMapper.updateById(vid);
                }
            } catch (Exception ignore) {}
        }
    }

    private String guessContentType(Path p) {
        String fn = p.getFileName().toString().toLowerCase();
        if (fn.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (fn.endsWith(".ts")) return "video/mp2t";
        if (fn.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream";
    }

    private void uploadToS3(Path filePath, String key, String contentType) {
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            PutObjectRequest por = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(por, RequestBody.fromBytes(bytes));
            log.info("Uploaded {} to s3://{}/{}", filePath.getFileName(), bucket, key);
        } catch (Exception e) {
            log.error("uploadToS3 failed for key={} file={} : {}", key, filePath.toAbsolutePath(), e.getMessage(), e);
            throw new RuntimeException("uploadToS3 failed for " + key + ": " + e.getMessage(), e);
        }
    }

    private int profileOrder(VideoTranscode t) {
        if (t == null || t.getRepresentationId() == null) return 99;
        return switch (t.getRepresentationId()) {
            case "1080p" -> 0;
            case "720p" -> 1;
            case "360p" -> 2;
            case "240p" -> 3;
            default -> 99;
        };
    }
}