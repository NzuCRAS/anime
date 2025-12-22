package com.anime.collection.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.collection.service.CollectionFolderLevel1Service;
import com.anime.common.dto.attachment.PresignRequestDTO;
import com.anime.common.dto.attachment.PresignResponseDTO;
import com.anime.common.dto.collection.leve1.*;
import com.anime.common.entity.collection.CollectionFolderLevel1;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.anime.common.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 一级收藏夹控制器
 */
@Tag(name = "CollectionFolderLevel1", description = "一级收藏夹管理接口")
@RestController
@Slf4j
@RequestMapping("/api/collection/folder/level1")
@RequiredArgsConstructor
public class CollectionFolderLevel1Controller {

    private final CollectionFolderLevel1Service collectionFolderLevel1Service;
    AttachmentService attachmentService;

    @Operation(summary = "获取 presign（Folder1 专用）", description = "生成 presigned PUT URL，供前端上传  相关附件")
    @PostMapping("/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequestDTO req, @CurrentUser Long userId) {
        String storagePath = "/Level1Cover/" + userId + "/"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
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

    @Operation(summary = "创建收藏夹", description = "为当前用户创建一级收藏夹（需要提供cover，name）")
    @PostMapping("/createNewFolder")
    public ResponseEntity<Result<String>> createNewFolder1(@RequestBody Leve1CreateDTO dto,
                                                              @CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (dto.getAttachmentId() == null || dto.getAttachmentId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "附件ID无效"));
            }
            List<CollectionFolderLevel1> existingFolders = collectionFolderLevel1Service.getCollectionFolderLevel1ByName(dto.getName(),currentUserId);
            if (existingFolders != null && !existingFolders.isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "用户已有收藏夹，无需重复创建"));
            }
            boolean created = collectionFolderLevel1Service.createNewFolder(dto.getName(), dto.getAttachmentId(), currentUserId);
            if (created) {
                return ResponseEntity.ok(Result.success("创建收藏夹成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "创建收藏夹失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "创建收藏夹失败：" + e.getMessage()));
        }
    }

    @Operation(summary = "获取用户一级收藏夹", description = "获取当前用户的所有一级收藏夹")
    @PostMapping("/getUserFolders")
    public ResponseEntity<Result<List<Level1ResultDTO>>> getUserFolders(@CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(Result.fail(ResultCode.UNAUTHORIZED, null));
            }
            List<Level1ResultDTO> folders = collectionFolderLevel1Service.getCollectionFolderLevel1(currentUserId);
            if (folders == null) folders = new ArrayList<>();
            return ResponseEntity.ok(Result.success(folders));
        } catch (Exception e) {
            log.error("getUserFolders failed for userId=" + currentUserId, e); // << 加上日志
            // 返回与 ResultCode 保持一致的 HTTP 状态（例如 SYSTEM_ERROR -> 500）
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body(Result.fail(ResultCode.SYSTEM_ERROR, null));
        }
    }

    @Operation(summary = "更新一级收藏夹名称", description = "根据 id 更新收藏夹名称")
    @PutMapping("/updateFolderName")
    public ResponseEntity<Result<String>> updateFolderName(@RequestBody Level1UpdateNameDTO dto,
                                                           @CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (dto.getId() == null || dto.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏夹ID无效"));
            }
            if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "新名称不能为空"));
            }
            boolean updated = collectionFolderLevel1Service.UpdateName(dto.getName(), dto.getId());
            if (updated) {
                return ResponseEntity.ok(Result.success("更新收藏夹名称成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "更新收藏夹名称失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "更新收藏夹名称失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "更新一级收藏夹封面", description = "更新指定一级收藏夹的封面 attachment id")
    @PutMapping("/updateFolderCover")
    public ResponseEntity<Result<String>> updateFolderCover(@RequestBody Leve1UpdateCoverDTO dto,
                                                            @CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (dto.getId() == null || dto.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏夹ID无效"));
            }
            if (dto == null || dto.getAttachment_id() == null || dto.getAttachment_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "封面路径不能为空"));
            }
            boolean updated = collectionFolderLevel1Service.UpdateCover(dto.getAttachment_id(), dto.getId());
            if (updated) {
                return ResponseEntity.ok(Result.success("更新收藏夹封面成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "更新收藏夹名称失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "更新收藏夹名称失败" + e.getMessage()));
        }
    }

    @Operation(summary = "删除一级收藏夹", description = "根据 id 删除一级收藏夹（可能会级联删除）")
    @DeleteMapping("/deleteFolder")
    public ResponseEntity<Result<String>> deleteFolder(@RequestBody Level1DeleteDTO dto,
                                                       @CurrentUser Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (dto.getId() == null || dto.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏夹ID无效"));
            }
            boolean deleted = collectionFolderLevel1Service.DeleteCollectionFolderLevel1(dto.getId());
            if (deleted) {
                return ResponseEntity.ok(Result.success("删除成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "删除失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "删除失败" + e.getMessage()));
        }
    }
}