package com.anime.video.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.common.dto.attachment.PresignRequestDTO;
import com.anime.common.dto.attachment.PresignResponseDTO;
import com.anime.common.dto.video.*;
import com.anime.common.entity.video.Video;
import com.anime.common.entity.video.VideoTranscode;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.anime.common.service.AttachmentService;
import com.anime.video.service.AbrService;
import com.anime.video.service.VideoLikeService;
import com.anime.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * VideoController: 视频相关接口（全部使用 POST）
 */
@Slf4j
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final VideoLikeService videoLikeService;
    private final AbrService abrService;
    private final AttachmentService attachmentService;

    @Operation(summary = "获取 presign（用户上传视频 专用）", description = "生成 presigned PUT URL，供前端上传视频")
    @PostMapping("/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequestDTO req, @CurrentUser Long userId) {
        String storagePath = "/video/" + userId + "/"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        log.info("presign request storagePath={} originalFilename={} mimeType={} uploadedBy={}",
                storagePath, req.getOriginalFilename(), req.getMimeType(), userId);
        try {
            PresignResponseDTO resp = attachmentService.preCreateAndPresign(
                    storagePath, req.getMimeType(), userId, req.getOriginalFilename(), null, null);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            log.error("presign failed", ex);
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body("presign failed");
        }
    }

    /**
     * 1. 查询所有视频（无分页），返回基本信息
     */
    @PostMapping("/list")
    public Result<List<Map<String, Object>>> listAll() {
        List<Video> list = videoService.listAllVideos();
        List<Map<String, Object>> out = list.stream().map(v -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", v.getId());
            m.put("title", v.getTitle());
            m.put("description", v.getDescription());
            m.put("likeCount", v.getLikeCount());
            m.put("createdAt", v.getCreatedAt());
            m.put("updatedAt", v.getUpdatedAt());
            m.put("uploaderId", v.getUploaderId());
            m.put("status", v.getStatus());
            return m;
        }).collect(Collectors.toList());
        return Result.success(out);
    }

    /**
     * 2. 查询单个视频详情（用于播放页） —— 包含 video 元数据与 transcode 列表
     */
    @PostMapping("/{videoId}/get")
    public Result<Map<String, Object>> getVideo(@PathVariable("videoId") Long videoId) {
        Video v = videoService.getVideoById(videoId);
        if (v == null) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", videoId);
            m.put("error", "not found");
            return Result.fail(ResultCode.NOT_FOUND, m);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("video", v);
        List<VideoTranscode> transcodes = videoService.listTranscodes(videoId);
        map.put("transcodes", transcodes);
        return Result.success(map);
    }

    /**
     * 5. 上传视频（用户确认信息后调用）：
     * 前端流程：先调用 Attachment presign -> 上传 -> 然后调用此接口连同 sourceAttachmentId 等信息
     * 创建 video 记录并自动触发后台转码任务（异步）
     */
    @PostMapping("")
    public Result<Map<String, Object>> createVideo(@RequestBody CreateVideoRequest req,
                                                   @CurrentUser Long userId) {
        if (req == null || req.getSourceAttachmentId() == null) {
            Map<String, Object> m = new HashMap<>();
            m.put("error", "invalid request or invalid sourceAttachmentId");
            return Result.fail(ResultCode.PARAM_ERROR, m);
        }
        Long uploaderId = userId != null ? userId : req.getUploaderId(); // 若 @CurrentUser 可用，优先使用
        Video v = videoService.createVideoRecord(uploaderId, req.getTitle(), req.getDescription(),
                req.getSourceAttachmentId(), req.getCoverAttachmentId());

        // 自动触发转码（异步）
        try {
            videoService.startTranscode(v.getId(), null); // null 表示使用默认 profile 列表
        } catch (Exception e) {
            log.error("startTranscode dispatch failed for videoId={}", v.getId(), e);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("videoId", v.getId());
        return Result.success(resp);
    }

    /**
     * 6. 删除视频（软删除）
     */
    @PostMapping("/{videoId}/delete")
    public Result<String> deleteVideo(@PathVariable("videoId") Long videoId,
                                      @CurrentUser Long userId) {
        try {
            boolean ok = videoService.deleteVideo(videoId, userId);
            if (!ok) {
                return Result.fail(ResultCode.FORBIDDEN, "not allowed or not found");
            }
            return Result.success("ok");
        } catch (Exception e) {
            log.error("deleteVideo error videoId={}", videoId, e);
            return Result.fail(ResultCode.SYSTEM_ERROR, "delete failed");
        }
    }

    /**
     * 3. 点赞 / 取消点赞（切换）
     */
    @PostMapping("/{videoId}/like")
    public Result<Map<String, Object>> toggleLike(@PathVariable("videoId") Long videoId,
                                                  @CurrentUser Long userId) {
        if (userId == null) {
            Map<String, Object> m = new HashMap<>();
            m.put("videoId", videoId);
            m.put("error", "unauthenticated");
            return Result.fail(ResultCode.UNAUTHORIZED, m);
        }
        boolean liked = videoLikeService.toggleLike(videoId, userId);
        Map<String, Object> m = new HashMap<>();
        m.put("liked", liked);
        m.put("likeCount", videoLikeService.getLikeCount(videoId));
        return Result.success(m);
    }

    /**
     * 4. 上报播放端 ABR / 卡顿 / 码率等指标（前端周期上报或播放结束汇报）
     */
    @PostMapping("/reportMetrics")
    public Result<String> reportMetrics(@RequestBody AbrReportRequest req,
                                        @CurrentUser Long userId) {
        if (req == null || req.getVideoId() == null || req.getSessionUuid() == null) {
            return Result.fail(ResultCode.PARAM_ERROR, "videoId and sessionUuid required");
        }
        try {
            abrService.reportMetrics(req, userId);
            return Result.success("ok");
        } catch (Exception e) {
            log.error("reportMetrics failed", e);
            return Result.fail(ResultCode.SYSTEM_ERROR, "report failed");
        }
    }

    /**
     * 播放 HLS：返回 master/variant playlist URL（这里返回单一 master url）
     */
    @PostMapping("/{videoId}/playHls")
    public Result<Map<String, String>> playHls(@PathVariable("videoId") Long videoId,
                                               @RequestParam(value = "expiry", required = false, defaultValue = "300") int expiry) {
        try {
            String url = videoService.getHlsMasterUrl(videoId, expiry);
            Map<String, String> map = new HashMap<>();
            map.put("url", url);
            return Result.success(map);
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            log.error("playHls error", e);
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }

    /**
     * 可选：列出每个视频对应的可播放 urls（各 variant playlist）
     */
    @PostMapping("/{videoId}/playUrls")
    public Result<List<Map<String, Object>>> getPlayUrls(@PathVariable("videoId") Long videoId,
                                                         @RequestParam(value = "expiry", required = false, defaultValue = "300") int expiry) {
        List<Map<String, Object>> urls = videoService.getPlayableUrls(videoId, expiry);
        return Result.success(urls);
    }

    /**
     * 后端代理/重写 HLS playlist：
     * - 如果 name == master.m3u8，则返回 master 内容，且把 variant 引用替换为后端此接口的 URL（由前端 baseUrl 拼接即可）
     * - 如果 name 是 variant playlist（stream_x.m3u8），则返回该 playlist 内容并把其中的 segment URI 替换为 S3 presigned URLs
     *
     * Example:
     * GET /api/videos/123/hls/playlist?name=master.m3u8&expiry=300
     */
    @GetMapping(value = "/{videoId}/hls/playlist", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> getHlsPlaylist(@PathVariable("videoId") Long videoId,
                                                 @RequestParam("name") String name,
                                                 @RequestParam(value = "expiry", required = false, defaultValue = "300") int expiry) {
        try {
            String content = videoService.getRewrittenHlsPlaylist(videoId, name, expiry);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/vnd.apple.mpegurl")
                    .body(content);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(ResultCode.NOT_FOUND.getCode()).body("not found");
        } catch (Exception e) {
            log.error("getHlsPlaylist failed for videoId={} name={}", videoId, name, e);
            return ResponseEntity.status(500).body("internal error");
        }
    }
}