package com.anime.collection.controller;

import com.anime.auth.service.JwtService;
import com.anime.collection.service.CollectionFolderLevel1Service;
import com.anime.common.dto.collection.Level1DTO;
import com.anime.common.entity.collection.CollectionFolderLevel1;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 一级收藏夹控制器
 * 处理一级收藏夹的CRUD操作，包含权限验证
 */
@RestController
@RequestMapping("/api/collection/folder/level1")
public class CollectionFolderLevel1Controller {

    private final CollectionFolderLevel1Service collectionFolderLevel1Service;
    private final JwtService jwtService;

    public CollectionFolderLevel1Controller(CollectionFolderLevel1Service collectionFolderLevel1Service,
                                            JwtService jwtService) {
        this.collectionFolderLevel1Service = collectionFolderLevel1Service;
        this.jwtService = jwtService;
    }

    /**
     * 创建默认收藏夹（不需要请求体，根据当前用户ID创建）
     * POST /api/collection/
     * 注意：每个用户只能创建一个默认收藏夹，重复创建会返回已存在
     */
    @PostMapping("/createDefaultFolder")
    public ResponseEntity<Result<String>> createDefaultFolder(HttpServletRequest request) {
        try {
            // 1. 验证Token和获取用户ID
            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED,"未授权或Token无效"));
            }

            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED,"用户ID无效"));
            }

            // 2. 检查用户是否已经有默认收藏夹
            // 这里可以调用Service检查用户是否已有收藏夹，避免重复创建
            List<CollectionFolderLevel1> existingFolders = collectionFolderLevel1Service.getCollectionFolderLevel1ByName();

            // 如果用户已有收藏夹，则不需要再创建默认收藏夹
            if (existingFolders != null && !existingFolders.isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR,"用户已有收藏夹，无需重复创建"));
            }

            // 3. 调用Service创建默认收藏夹
            boolean created = collectionFolderLevel1Service.createNewFolder(userId);

            if (created) {
                return ResponseEntity.ok(Result.success("创建默认收藏夹成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR,"创建默认收藏夹失败"));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR,"创建默认收藏夹失败：" + e.getMessage()));
        }
    }

    /** * 获取当前用户的所有一级收藏夹 */
    @GetMapping("/getUserFolders")
    public ResponseEntity<Result<List<CollectionFolderLevel1>>> getUserFolders(HttpServletRequest request) {
        try {
            // 从请求中获取并验证Access Token
            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, null));
            }
            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, null));
            }
            List<CollectionFolderLevel1> folders = collectionFolderLevel1Service.getCollectionFolderLevel1(userId);
            // 如果获取不到文件链表或没有收藏夹，返回空列表而不是null
            if (folders == null) {
                folders = new ArrayList<>();
            }
            return ResponseEntity.ok(Result.success(folders));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, null));
        }
    }

    /**
     * 更新一级收藏夹名称
     */
    @PutMapping("/updateFolderName")
    public ResponseEntity<Result<String>> updateFolderName(@RequestBody Level1DTO level1DTO, HttpServletRequest request) {
        try {
            if (level1DTO.getId() == null || level1DTO.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST,"收藏夹ID无效"));
            }

            if (level1DTO == null || level1DTO.getName() == null || level1DTO.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST,"新名称不能为空"));
            }

            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED,"未授权或Token无效"));
            }

            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED,"用户ID无效"));
            }

            boolean updated = collectionFolderLevel1Service.UpdateName(level1DTO.getName(), level1DTO.getId());

            if (updated) {
                return ResponseEntity.ok(Result.success("更新收藏夹名称成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR,"更新收藏夹名称失败"));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR,"更新收藏夹名称失败: " + e.getMessage()));
        }
    }

    /**
     * 更新一级收藏夹封面
     */
    @PutMapping("/updateFolderCover")
    public ResponseEntity<Result<String>> updateFolderCover(@RequestBody Level1DTO level1DTO, HttpServletRequest request) {
        try {
            if (level1DTO.getId() == null || level1DTO.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST,"收藏夹ID无效"));
            }

            if (level1DTO == null ||level1DTO.getAttachment_id() == null || level1DTO.getAttachment_id() <=0 ) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST,"封面路径不能为空"));
            }

            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED,"未授权或Token无效"));
            }

            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED,"用户ID无效"));
            }

            boolean updated = collectionFolderLevel1Service.UpdateCover(level1DTO.getAttachment_id(),level1DTO.getId());

            if (updated) {
                return ResponseEntity.ok(Result.success("更新收藏夹封面成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR,"更新收藏夹名称失败"));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR,"更新收藏夹名称失败" + e.getMessage()));
        }
    }

    /**
     * 删除一级收藏夹，级联删除
     */
    @GetMapping("/deleteFolder")
    public ResponseEntity<Result<String>> deleteFolder(@RequestBody Level1DTO level1DTO,HttpServletRequest request) {
        try {
            if (level1DTO.getId() == null || level1DTO.getId() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST,"收藏夹ID无效"));
            }

            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED,"未授权或Token无效"));
            }

            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED,"用户ID无效"));
            }

            boolean deleted = collectionFolderLevel1Service.DeleteCollectionFolderLevel1(level1DTO.getId());

            if (deleted) {
                return ResponseEntity.ok(Result.success("删除成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR,"删除失败"));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR,"删除失败" + e.getMessage()));
        }
    }

    /**
     * 从请求中提取Access Token
     * 支持从Authorization头和Cookie中获取
     */
    /*private String extractAccessTokenFromRequest(HttpServletRequest request) {
        // 1. 尝试从Authorization头获取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. 尝试从Cookie获取
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }*/

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