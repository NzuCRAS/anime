package com.anime.collection.controller;

import com.anime.auth.service.JwtService;
import com.anime.collection.service.CollectedItemService;
import com.anime.common.dto.collection.CollectedItemDTO;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 收藏项控制器
 */
@RestController
@RequestMapping("/api/collection/item")
public class CollectedItemController {

    private final CollectedItemService collectedItemService;
    private final JwtService jwtService;

    public CollectedItemController(CollectedItemService collectedItemService, JwtService jwtService) {
        this.collectedItemService = collectedItemService;
        this.jwtService = jwtService;
    }

    /**
     * 接口1：创建自定义收藏项（带图片、自定义名称和描述）
     * POST /api/collection/item/createCustom
     */
    @PostMapping("/createCustom")
    public ResponseEntity<Result<String>> createCustom(@RequestBody CollectedItemDTO dto, HttpServletRequest request) {
        try {
            // 验证 Token
            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权或Token无效"));
            }
            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "用户ID无效"));
            }

            // 参数校验
            if (dto.getAttachment_id() == null || dto.getAttachment_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "封面附件ID无效"));
            }
            if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "名称不能为空"));
            }
            if (dto.getFather_level2_id() == null || dto.getFather_level2_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "所属二级收藏夹ID无效"));
            }

            boolean created = collectedItemService.createWithCustom(
                    dto.getAttachment_id(),
                    dto.getName(),
                    dto.getDescription(),
                    dto.getFather_level2_id()
            );

            if (created) {
                return ResponseEntity.ok(Result.success("收藏项创建成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "收藏项创建失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "系统异常: " + e.getMessage()));
        }
    }

    /**
     * 接口2：创建默认收藏项（使用默认封面和名称，仅描述可编辑）
     * POST /api/collection/item/createDefault
     */
    @PostMapping("/createDefault")
    public ResponseEntity<Result<String>> createDefault(@RequestBody CollectedItemDTO dto, HttpServletRequest request) {
        try {
            // 验证 Token
            String accessToken = extractAccessTokenFromRequest(request);
            if (accessToken == null || !jwtService.validateToken(accessToken)) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "未授权或Token无效"));
            }
            Long userId = jwtService.extractUserId(accessToken);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.UNAUTHORIZED, "用户ID无效"));
            }

            // 仅需校验 folderLevel2Id
            if (dto.getFather_level2_id() == null || dto.getFather_level2_id() <= 0) {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "所属二级收藏夹ID无效"));
            }

            boolean created = collectedItemService.createWithDefault(
                    dto.getDescription(),
                    dto.getFather_level2_id()
            );

            if (created) {
                return ResponseEntity.ok(Result.success("默认收藏项创建成功"));
            } else {
                return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "默认收藏项创建失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "系统异常: " + e.getMessage()));
        }
    }

    /**
     * 更新收藏项封面（attachmentId）
     * PUT /api/collection/item/update/cover
     */
    @PutMapping("/update/cover")
    public ResponseEntity<Result<String>> updateCover(@RequestBody CollectedItemDTO dto, HttpServletRequest request) {
        validateTokenAndExtractUserId(request); // 抽取公共逻辑（见下方）
        Long userId = jwtService.extractUserId(extractAccessTokenFromRequest(request));

        if (dto.getId() == null || dto.getId() <= 0) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏项ID无效"));
        }
        if (dto.getAttachment_id() == null || dto.getAttachment_id() <= 0) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "封面附件ID无效"));
        }

        boolean updated = collectedItemService.updateAttachmentId(dto.getId(), dto.getAttachment_id(), userId);
        if (updated) {
            return ResponseEntity.ok(Result.success("封面更新成功"));
        } else {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "封面更新失败"));
        }
    }

    /**
     * 更新收藏项名称
     * PUT /api/collection/item/update/name
     */
    @PutMapping("/update/name")
    public ResponseEntity<Result<String>> updateName(@RequestBody CollectedItemDTO dto, HttpServletRequest request) {
        validateTokenAndExtractUserId(request);
        Long userId = jwtService.extractUserId(extractAccessTokenFromRequest(request));

        if (dto.getId() == null || dto.getId() <= 0) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏项ID无效"));
        }
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "名称不能为空"));
        }

        boolean updated = collectedItemService.updateName(dto.getId(), dto.getName(), userId);
        if (updated) {
            return ResponseEntity.ok(Result.success("名称更新成功"));
        } else {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "名称更新失败"));
        }
    }

    /**
     * 更新收藏项描述
     * PUT /api/collection/item/update/description
     */
    @PutMapping("/update/description")
    public ResponseEntity<Result<String>> updateDescription(@RequestBody CollectedItemDTO dto, HttpServletRequest request) {
        validateTokenAndExtractUserId(request);
        Long userId = jwtService.extractUserId(extractAccessTokenFromRequest(request));

        if (dto.getId() == null || dto.getId() <= 0) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "收藏项ID无效"));
        }

        boolean updated = collectedItemService.updateDescription(dto.getId(), dto.getDescription(), userId);
        if (updated) {
            return ResponseEntity.ok(Result.success("描述更新成功"));
        } else {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.SYSTEM_ERROR, "描述更新失败"));
        }
    }

    private void validateTokenAndExtractUserId(HttpServletRequest request) {
        String accessToken = extractAccessTokenFromRequest(request);
        if (accessToken == null || !jwtService.validateToken(accessToken)) {
            throw new RuntimeException("UNAUTHORIZED");
        }
        Long userId = jwtService.extractUserId(accessToken);
        if (userId == null) {
            throw new RuntimeException("INVALID_USER");
        }
    }

    // 重写 extractAccessTokenFromRequest（仅从 Header）
    private String extractAccessTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }
}