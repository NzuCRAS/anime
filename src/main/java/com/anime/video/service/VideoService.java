package com.anime.video.service;

import com.anime.common.entity.video.Video;
import com.anime.common.entity.video.VideoTranscode;
import com.anime.common.mapper.video.VideoMapper;
import com.anime.common.mapper.video.VideoTranscodeMapper;
import com.anime.common.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Video 业务服务：创建记录、启动转码（自动生成标准档位）、查询播放 url 等
 */
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoMapper videoMapper;
    private final VideoTranscodeMapper transcodeMapper;
    private final AttachmentService attachmentService;
    private final TranscodeJobDispatcher transcodeJobDispatcher;

    private static final List<Map<String, Object>> STANDARD_PROFILES = List.of(
            Map.of("representationId", "1080p", "bitrate", 3500000, "resolution", "1920x1080"),
            Map.of("representationId", "720p",  "bitrate", 1800000, "resolution", "1280x720"),
            Map.of("representationId", "360p",  "bitrate", 650000,  "resolution", "640x360"),
            Map.of("representationId", "240p",  "bitrate", 400000,  "resolution", "426x240")
    );

    @Transactional
    public Video createVideoRecord(Long uploaderId, String title, String description, Long sourceAttachmentId, Long coverAttachmentId) {
        Video v = new Video();
        v.setUploaderId(uploaderId);
        v.setTitle(title);
        v.setDescription(description);
        v.setSourceAttachmentId(sourceAttachmentId);
        v.setCoverAttachmentId(coverAttachmentId);
        v.setStatus("uploading");
        v.setCreatedAt(java.time.LocalDateTime.now());
        videoMapper.insert(v);
        return v;
    }

    public Video getVideoById(Long videoId) {
        return videoMapper.selectById(videoId);
    }

    public List<VideoTranscode> listTranscodes(Long videoId) {
        return transcodeMapper.listByVideoId(videoId);
    }

    public List<Video> listAllVideos() {
        return videoMapper.selectList(null);
    }

    /**
     * 启动转码：如果 representations == null 则使用 STANDARD_PROFILES
     * 本方法只负责：在 DB 中写入 transcode 条目并把 video.status 设为 processing；
     * 实际转码由异步 worker 处理（dispatch videoId）。
     */
    @Transactional
    public void startTranscode(Long videoId, List<Map<String, Object>> representations) {
        Video v = videoMapper.selectById(videoId);
        if (v == null) throw new IllegalArgumentException("video not found: " + videoId);
        // set video status
        v.setStatus("processing");
        videoMapper.updateById(v);

        List<Map<String, Object>> reps = representations;
        if (reps == null || reps.isEmpty()) {
            reps = STANDARD_PROFILES;
        }

        for (Map<String, Object> r : reps) {
            VideoTranscode t = new VideoTranscode();
            t.setVideoId(videoId);
            t.setRepresentationId((String) r.get("representationId"));
            t.setBitrate(((Number) r.getOrDefault("bitrate", 800_000)).intValue());
            t.setResolution((String) r.getOrDefault("resolution", null));
            t.setStatus("processing");
            t.setCreatedAt(java.time.LocalDateTime.now());
            transcodeMapper.insert(t);
        }

        // dispatch one job for the whole video (worker will generate multi-variant HLS and update each transcode record)
        transcodeJobDispatcher.dispatchTranscodeJob(videoId);
    }

    /**
     * 返回 HLS master playlist 的 presigned url（由 worker 产生 master.m3u8 存在 S3）
     */
    public String getHlsMasterUrl(Long videoId, int expirySeconds) {
        // convention: master stored at videos/{videoId}/hls/master.m3u8
        String masterKey = String.format("videos/%d/hls/master.m3u8", videoId);
        return attachmentService.generatePresignedGetUrlByKey(masterKey, expirySeconds);
    }

    /**
     * 读取 S3 上指定的 playlist（storageKey = videos/{videoId}/hls/{name}），
     * 并返回重写后的内容：
     * - master.m3u8: 将每个 variant playlist 的相对引用替换为后端此接口的绝对 URL（前端会以 baseUrl 调用）
     * - variant playlist: 将每个 segment 文件（相对路径）替换为 presigned GET URL（S3）
     */
    public String getRewrittenHlsPlaylist(Long videoId, String name, int expirySeconds) throws Exception {
        if (videoMapper.selectById(videoId) == null) throw new IllegalArgumentException("video not found");
        String basePrefix = String.format("videos/%d/hls", videoId);
        String key = basePrefix + "/" + name;

        // 1) 获取原始 playlist 内容（通过 presigned URL 再请求，server 端有网络权限）
        String presigned = attachmentService.generatePresignedGetUrlByKey(key, Math.max(60, expirySeconds));
        if (presigned == null) throw new IllegalArgumentException("playlist not found: " + key);

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(20)).build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(presigned)).GET().build();
        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) throw new IllegalArgumentException("failed to fetch playlist: " + key);

        String content = resp.body();
        StringBuilder out = new StringBuilder();
        String[] lines = content.split("\\r?\\n");

        boolean isMaster = name.toLowerCase().endsWith("master.m3u8") || name.equalsIgnoreCase("master.m3u8");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                out.append(line).append("\n");
                continue;
            }
            // line is a URI (relative or absolute)
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                out.append(line).append("\n");
                continue;
            }

            // If master playlist: replace variant reference with backend proxy endpoint
            if (isMaster) {
                // Build URL that frontend can call (relative path). Frontend will prefix baseUrl.
                // Encode the name param
                String encoded = java.net.URLEncoder.encode(trimmed, java.nio.charset.StandardCharsets.UTF_8);
                String proxy = String.format("/api/videos/%d/hls/playlist?name=%s&expiry=%d", videoId, encoded, expirySeconds);
                out.append(proxy).append("\n");
            } else {
                // variant playlist: replace segment filename with presigned S3 URL
                // compute storage key for the segment: if trimmed is relative like "seg_000.ts" or "subdir/seg_000.ts"
                String segmentKey;
                if (trimmed.startsWith("/")) {
                    // absolute path inside bucket (rare)
                    segmentKey = trimmed.replaceFirst("^/+", "");
                } else {
                    segmentKey = basePrefix + "/" + trimmed;
                }
                // generate presigned GET for the segment
                String segUrl = attachmentService.generatePresignedGetUrlByKey(segmentKey, Math.max(60, expirySeconds));
                if (segUrl == null) {
                    // fallback: keep original (may fail)
                    out.append(line).append("\n");
                } else {
                    out.append(segUrl).append("\n");
                }
            }
        }

        return out.toString();
    }

    /**
     * 返回各变体 playlist 的 presigned urls（供前端显示手动选项）
     */
    public List<Map<String, Object>> getPlayableUrls(Long videoId, int expirySeconds) {
        List<VideoTranscode> transcodes = transcodeMapper.listByVideoId(videoId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (VideoTranscode t : transcodes) {
            if (!"ready".equalsIgnoreCase(t.getStatus())) continue;
            String manifestPath = t.getManifestPath();
            if (manifestPath == null) continue;
            String url = attachmentService.generatePresignedGetUrlByKey(manifestPath, expirySeconds);
            Map<String, Object> m = new HashMap<>();
            m.put("representationId", t.getRepresentationId());
            m.put("bitrate", t.getBitrate());
            m.put("resolution", t.getResolution());
            m.put("url", url);
            out.add(m);
        }
        return out;
    }

    /**
     * 删除视频（软删除）：仅允许上传者删除
     */
    @Transactional
    public boolean deleteVideo(Long videoId, Long operatorUserId) {
        Video v = videoMapper.selectById(videoId);
        if (v == null) return false;
        if (operatorUserId != null && !operatorUserId.equals(v.getUploaderId())) {
            // only uploader allowed to delete for now
            return false;
        }
        v.setStatus("deleted");
        videoMapper.updateById(v);
        // mark transcodes as failed/deleted
        var trans = transcodeMapper.listByVideoId(videoId);
        for (VideoTranscode t : trans) {
            t.setStatus("failed");
            transcodeMapper.updateById(t);
        }
        return true;
    }
}