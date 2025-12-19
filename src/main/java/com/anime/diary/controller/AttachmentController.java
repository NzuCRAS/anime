package com.anime.diary.controller;

import com.anime.common.dto.attachment.PresignRequestDTO;
import com.anime.common.dto.attachment.PresignResponseDTO;
import com.anime.common.entity.attachment.Attachment;
import com.anime.common.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@Tag(name = "Attachment", description = "附件上传与 presign 相关接口")
@Slf4j
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Operation(summary = "上传完成通知", description = "前端 PUT 到 presigned URL 后调用，后端将 attachment 标记为 available 并更新 metadata")
    @PostMapping("/complete")
    public ResponseEntity<Attachment> complete(@RequestBody CompleteRequest req) {
        log.info("complete request attachmentId={}", req.getAttachmentId());
        Attachment a = attachmentService.completeUpload(req.getAttachmentId());
        return ResponseEntity.ok(a);
    }

    @Operation(summary = "获取 presigned GET URL", description = "返回短期有效的 GET URL")
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
    public static class CompleteRequest {
        private Long attachmentId;
    }
}