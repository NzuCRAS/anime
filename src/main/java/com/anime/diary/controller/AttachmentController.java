package com.anime.diary.controller;

import com.anime.common.dto.attachment.PresignResponseDTO;
import com.anime.common.entity.attachment.Attachment;
import com.anime.common.service.AttachmentService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

/**
 * 简单的 Attachment Controller，用于本地联调和 smoke-test。
 */
@Slf4j
@RestController
@RequestMapping("/api/attachments")
@CrossOrigin(origins = "http://localhost:8080") // 开发时方便测试；生产环境请限制来源
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * 预创建并生成 presigned PUT URL（带异常捕获，开发时方便定位）
     */
    @PostMapping("/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequest req) {
        log.info("presign request storagePath={} originalFilename={} contentType={} uploadedBy={}",
                req.getStoragePath(), req.getOriginalFilename(), req.getContentType(), req.getUploadedBy());
        try {
            PresignResponseDTO resp = attachmentService.preCreateAndPresign(
                    req.getStoragePath(), req.getContentType(), req.getUploadedBy(), req.getOriginalFilename());
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            // 把异常栈写入日志，返回简短错误给前端（开发时可返回 ex.getMessage()）
            log.error("presign failed", ex);
            return ResponseEntity.status(500).body(Collections.singletonMap("error", ex.getMessage()));
        }
    }

    /**
     * 上传完成通知：前端在 PUT 成功后调用此接口以完成 DB 状态更新
     */
    @PostMapping("/complete")
    public ResponseEntity<Attachment> complete(@RequestBody CompleteRequest req) {
        log.info("complete request attachmentId={}", req.getAttachmentId());
        Attachment a = attachmentService.completeUpload(req.getAttachmentId());
        return ResponseEntity.ok(a);
    }

    /**
     * 返回 presigned GET
     */
    @GetMapping("/{id}/presigned-get")
    public ResponseEntity<Map<String, String>> presignedGet(
            @PathVariable("id") Long id,
            @RequestParam(name = "expiry", defaultValue = "300") long expirySeconds) {

        String url = attachmentService.generatePresignedGetUrl(id, expirySeconds);
        return ResponseEntity.ok(Collections.singletonMap("url", url));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignRequest {
        private String storagePath;
        private String originalFilename;
        private String contentType;
        private Long uploadedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteRequest {
        private Long attachmentId;
    }
}