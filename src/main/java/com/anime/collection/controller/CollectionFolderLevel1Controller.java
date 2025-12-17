package com.anime.collection.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.collection.service.CollectionFolderLevel1Service;
import com.anime.common.dto.collection.leve1.Leve1UpdateCoverDTO;
import com.anime.common.dto.collection.leve1.Level1DeleteDTO;
import com.anime.common.dto.collection.leve1.Level1UpdateNameDTO;
import com.anime.common.entity.collection.CollectionFolderLevel1;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 一级收藏夹控制器
 */
@Tag(name = "CollectionFolderLevel1", description = "一级收藏夹管理接口")
@RestController
@RequestMapping("/api/collection/folder/level1")
@RequiredArgsConstructor
public class CollectionFolderLevel1Controller {

    private final CollectionFolderLevel1Service collectionFolderLevel1Service;

    @Operation(summary = "创建默认收藏夹", description = "为当前用户创建默认一级收藏夹（若已存在则返回失败）")
    @PostMapping("/createDefaultFolder")
    public ResponseEntity<Result<String>> createDefaultFolder(@CurrentUser Long currentUserId) {
        // ... 原实现不变 ...
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            List<CollectionFolderLevel1> existingFolders = collectionFolderLevel1Service.getCollectionFolderLevel1ByName(currentUserId);
            if (existingFolders != null && !existingFolders.isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "用户已有收藏夹，无需重复创建"));
            }
            boolean created = collectionFolderLevel1Service.createNewFolder(currentUserId);
            if (created) {
                return ResponseEntity.ok(Result.success("创建默认收藏夹成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "创建默认收藏夹失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "创建默认收藏夹失败：" + e.getMessage()));
        }
    }

    @Operation(summary = "获取用户一级收藏夹", description = "获取当前用户的所有一级收藏夹")
    @GetMapping("/getUserFolders")
    public ResponseEntity<Result<List<CollectionFolderLevel1>>> getUserFolders(@CurrentUser Long currentUserId) {
        // ... 原实现不变 ...
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, null));
            }
            List<CollectionFolderLevel1> folders = collectionFolderLevel1Service.getCollectionFolderLevel1(currentUserId);
            if (folders == null) folders = new ArrayList<>();
            return ResponseEntity.ok(Result.success(folders));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, null));
        }
    }

    @Operation(summary = "更新一级收藏夹名称", description = "根据 id 更新收藏夹名称")
    @PutMapping("/updateFolderName")
    public ResponseEntity<Result<String>> updateFolderName(@RequestBody Level1UpdateNameDTO level1DTO,
                                                           @CurrentUser Long currentUserId) {
        // ... 原实现不变 ...
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (level1DTO.getId() == null || level1DTO.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏夹ID无效"));
            }
            if (level1DTO == null || level1DTO.getName() == null || level1DTO.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "新名称不能为空"));
            }
            boolean updated = collectionFolderLevel1Service.UpdateName(level1DTO.getName(), level1DTO.getId());
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
    public ResponseEntity<Result<String>> updateFolderCover(@RequestBody Leve1UpdateCoverDTO level1DTO,
                                                            @CurrentUser Long currentUserId) {
        // ... 原实现不变 ...
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (level1DTO.getId() == null || level1DTO.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏夹ID无效"));
            }
            if (level1DTO == null || level1DTO.getAttachment_id() == null || level1DTO.getAttachment_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "封面路径不能为空"));
            }
            boolean updated = collectionFolderLevel1Service.UpdateCover(level1DTO.getAttachment_id(), level1DTO.getId());
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
    public ResponseEntity<Result<String>> deleteFolder(@RequestBody Level1DeleteDTO level1DTO,
                                                       @CurrentUser Long currentUserId) {
        // ... 原实现不变 ...
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (level1DTO.getId() == null || level1DTO.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏夹ID无效"));
            }
            boolean deleted = collectionFolderLevel1Service.DeleteCollectionFolderLevel1(level1DTO.getId());
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