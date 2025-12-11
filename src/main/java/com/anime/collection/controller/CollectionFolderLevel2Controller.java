package com.anime.collection.controller;

import com.anime.auth.service.JwtService;
import com.anime.collection.service.CollectionFolderLevel2Service;
import com.anime.common.dto.collection.Level2DTO;
import com.anime.common.entity.collection.CollectionFolderLevel2;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 二级收藏夹控制器
 * 处理二级收藏夹的CRUD操作，包含权限验证（通过一级收藏夹归属间接控制）
 */
@RestController
@RequestMapping("/api/collection/folder/level2")
public class CollectionFolderLevel2Controller {

    private final CollectionFolderLevel2Service collectionFolderLevel2Service;
    private final JwtService jwtService;

    public CollectionFolderLevel2Controller(CollectionFolderLevel2Service collectionFolderLevel2Service, JwtService jwtService) {
        this.collectionFolderLevel2Service = collectionFolderLevel2Service;
        this.jwtService = jwtService;
    }

    /**
     * 创建默认二级收藏夹（需要指定父级一级收藏夹ID）
     * POST /api/collection/folder/level2/createDefaultFolder
     */
    @PostMapping("/createDefaultFolder")
    public ResponseEntity<Result<String>> createDefaultFolder(@RequestBody Level2DTO level2DTO, HttpServletRequest request) {
        try {
            // 1. 验证Token和获取用户ID（虽然不直接用userId做校验，但确保是合法用户）
            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权或Token无效"));
            }
            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "用户ID无效"));
            }

            // 2. 校验父级收藏夹ID
            if (level2DTO.getFather_id() == null || level2DTO.getFather_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "父级收藏夹ID无效"));
            }

            // 3. 检查是否已有默认二级收藏夹（名称为“默认收藏夹”）
            List<CollectionFolderLevel2> existingFolders = collectionFolderLevel2Service.getCollectionFolderLevel2ByName();
            if (existingFolders != null && !existingFolders.isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "已存在默认二级收藏夹，无需重复创建"));
            }

            // 4. 调用Service创建默认二级收藏夹
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

    /**
     * 获取指定一级收藏夹下的所有二级收藏夹
     * GET /api/collection/folder/level2/getFoldersByParent
     */
    @GetMapping("/getFoldersByParent")
    public ResponseEntity<Result<List<CollectionFolderLevel2>>> getFoldersByParent(@RequestBody Level2DTO level2DTO, HttpServletRequest request) {
        try {
            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, null));//未授权
            }
            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, null));//用户不存在
            }

            if (level2DTO.getFather_id() == null || level2DTO.getFather_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, null));//父文件夹id错误
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

    /**
     * 更新二级收藏夹名称
     * PUT /api/collection/folder/level2/updateFolderName
     */
    @PutMapping("/updateFolderName")
    public ResponseEntity<Result<String>> updateFolderName(@RequestBody Level2DTO level2DTO, HttpServletRequest request) {
        try {
            if (level2DTO.getId() == null || level2DTO.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏夹ID无效"));
            }
            if (level2DTO.getName() == null || level2DTO.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "新名称不能为空"));
            }

            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权或Token无效"));
            }
            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "用户ID无效"));
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

    /**
     * 删除二级收藏夹（级联删除其下内容，如有）
     * GET /api/collection/folder/level2/deleteFolder
     */
    @GetMapping("/deleteFolder")
    public ResponseEntity<Result<String>> deleteFolder(@RequestBody Level2DTO level2DTO, HttpServletRequest request) {
        try {
            if (level2DTO.getId() == null || level2DTO.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏夹ID无效"));
            }

            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权或Token无效"));
            }
            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "用户ID无效"));
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
    /**
     * 从请求中提取Access Token
     * 仅从 Authorization header 获取
     */
    private String extractAccessTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
}