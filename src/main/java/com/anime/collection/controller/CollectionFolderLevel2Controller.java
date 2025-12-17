package com.anime.collection.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.collection.service.CollectionFolderLevel2Service;
import com.anime.common.dto.collection.level2.Level2DeleteDTO;
import com.anime.common.dto.collection.level2.Level2UpdateNameDTO;
import com.anime.common.dto.collection.level2.Level2CreateOrGetDTO;
import com.anime.common.entity.collection.CollectionFolderLevel2;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "CollectionFolderLevel2", description = "二级收藏夹管理接口")
@RestController
@RequestMapping("/api/collection/folder/level2")
@RequiredArgsConstructor
public class CollectionFolderLevel2Controller {

    private final CollectionFolderLevel2Service collectionFolderLevel2Service;

    @Operation(summary = "创建默认二级收藏夹", description = "在指定父级一级收藏夹下创建默认二级收藏夹")
    @PostMapping("/createDefaultFolder")
    public ResponseEntity<Result<String>> createDefaultFolder(@RequestBody Level2CreateOrGetDTO level2DTO,
                                                              @CurrentUser Long currentUserId) {
        // ... 原实现不变 ...
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (level2DTO.getFather_id() == null || level2DTO.getFather_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "父级收藏夹ID无效"));
            }
            List<CollectionFolderLevel2> existingFolders = collectionFolderLevel2Service.getCollectionFolderLevel2ByName(currentUserId);
            if (existingFolders != null && !existingFolders.isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "已存在默认二级收藏夹，无需重复创建"));
            }
            boolean created = collectionFolderLevel2Service.createNewFolder(level2DTO.getFather_id());
            if (created) {
                return ResponseEntity.ok(Result.success("创建默认二级收藏夹成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "创建默认二级收藏夹失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "创建默认二级收藏夹失败：" + e.getMessage()));
        }
    }

    @Operation(summary = "获取父级下的二级收藏夹", description = "根据父级一级收藏夹 ID 获取其下的所有二级收藏夹")
    @GetMapping("/getFoldersByParent")
    public ResponseEntity<Result<List<CollectionFolderLevel2>>> getFoldersByParent(@RequestBody Level2CreateOrGetDTO level2DTO,
                                                                                   @CurrentUser Long currentUserId) {
        // ... 原实现不变 ...
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, null));
            }
            if (level2DTO.getFather_id() == null || level2DTO.getFather_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, null));
            }
            List<CollectionFolderLevel2> folders = collectionFolderLevel2Service.getCollectionFolderLevel(level2DTO.getFather_id());
            if (folders == null) {
                folders = new ArrayList<>();
            }
            return ResponseEntity.ok(Result.success(folders));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, null));
        }
    }

    @Operation(summary = "更新二级收藏夹名称", description = "根据 id 更新二级收藏夹名称")
    @PutMapping("/updateFolderName")
    public ResponseEntity<Result<String>> updateFolderName(@RequestBody Level2UpdateNameDTO level2DTO,
                                                           @CurrentUser Long currentUserId) {
        // ... 原实现不变 ...
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (level2DTO.getId() == null || level2DTO.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏夹ID无效"));
            }
            if (level2DTO.getName() == null || level2DTO.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "新名称不能为空"));
            }
            boolean updated = collectionFolderLevel2Service.UpdateName(level2DTO.getName(), level2DTO.getId());
            if (updated) {
                return ResponseEntity.ok(Result.success("更新二级收藏夹名称成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "更新二级收藏夹名称失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "更新二级收藏夹名称失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "删除二级收藏夹", description = "根据 id 删除二级收藏夹（可能会级联删除）")
    @DeleteMapping("/deleteFolder")
    public ResponseEntity<Result<String>> deleteFolder(@RequestBody Level2DeleteDTO level2DTO,
                                                       @CurrentUser Long currentUserId) {
        // ... 原实现不变 ...
        try {
            if (currentUserId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
            }
            if (level2DTO.getId() == null || level2DTO.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏夹ID无效"));
            }
            boolean deleted = collectionFolderLevel2Service.DeleteCollectionFolderLevel2(level2DTO.getId());
            if (deleted) {
                return ResponseEntity.ok(Result.success("删除二级收藏夹成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "删除二级收藏夹失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "删除二级收藏夹失败: " + e.getMessage()));
        }
    }
}