package com.anime.user.controller;

import com.anime.auth.service.AccessTokenBlacklistService;
import com.anime.auth.service.JwtService;
import com.anime.auth.service.RefreshTokenService;
import com.anime.auth.utils.JwtCookieUtil;
import com.anime.auth.web.CurrentUser;
import com.anime.common.dto.attachment.PresignRequestDTO;
import com.anime.common.dto.attachment.PresignResponseDTO;
import com.anime.common.dto.user.*;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.anime.common.service.AttachmentService;
import com.anime.config.JwtProperties;
import com.anime.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 登录/注册接口（优化后）
 */
@Tag(name = "User", description = "用户登录 / 注册 / logout / ping 等接口")
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final JwtService jwtService;
    private final AttachmentService attachmentService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final JwtProperties jwtProperties;
    private final AccessTokenBlacklistService accessTokenBlacklistService;

    private final static UserInfoDTO failUserInfoDTO = new UserInfoDTO();

    @Operation(summary = "登录（支持 Authorization header / refresh cookie / 凭证三种方式）", description = "优先使用 Authorization header；其次尝试 refresh cookie；否则使用用户名密码登录")
    @PostMapping("/login")
    public ResponseEntity<Result<UserInfoDTO>> login(
            @RequestBody(required = false) UserLoginDTO loginDTO,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        // Helper to build UserInfoDTO for a given userId
        java.util.function.Function<Long, UserInfoDTO> buildUserInfo = (uid) -> {
            UserInfoDTO info = new UserInfoDTO();
            info.setId(uid == null ? null : String.valueOf(uid));
            try {
                info.setUsername(userService.getUsernameById(uid));
            } catch (Exception ex) {
                log.debug("failed to get username for userId={}: {}", uid, ex.getMessage());
            }

            // Try to obtain user's avatar attachment id from userService (method name may differ in your codebase)
            Long avatarAttachmentId = null;
            try {
                // NOTE: adjust the method name below to match your UserService API if different.
                avatarAttachmentId = userService.getAvatarAttachmentId(uid);
            } catch (Throwable ignore) {
                // If your UserService doesn't expose avatar id, ignore and leave avatarAttachmentId==null
                log.debug("getAvatarAttachmentId not available or failed for userId={}: {}", uid, ignore.getMessage());
            }

            if (avatarAttachmentId != null) {
                try {
                    // generate a short-lived presigned GET url (e.g. 300 seconds)
                    String url = attachmentService.generatePresignedGetUrl(avatarAttachmentId, 300L);
                    info.setUserAvatarUrl(url);
                } catch (Exception e) {
                    log.debug("failed to generate presigned url for avatar attachment {}: {}", avatarAttachmentId, e.getMessage());
                    info.setUserAvatarUrl(null);
                }
            } else {
                info.setUserAvatarUrl(null);
            }

            // personalSignature - try to get from userService if available
            try {
                String sig = userService.getPersonalSignature(uid);
                info.setPersonalSignature(sig);
            } catch (Throwable ignore) {
                log.debug("getPersonalSignature not available or failed for userId={}: {}", uid, ignore.getMessage());
                info.setPersonalSignature(null);
            }

            return info;
        };

        // 1) If Authorization header already has a valid access token, accept it and return user info
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String accessToken = authorizationHeader.substring("Bearer ".length()).trim();
            try {
                if (jwtService.validateToken(accessToken) && jwtService.isAccessToken(accessToken)) {
                    Long userId = jwtService.extractUserId(accessToken);
                    userService.onLoginSuccess(userId);

                    // return user info payload and keep New-Access-Token header for convenience
                    response.setHeader("New-Access-Token", accessToken);
                    UserInfoDTO dto = buildUserInfo.apply(userId);
                    return ResponseEntity.ok(Result.success(dto));
                }
            } catch (Exception ignored) {
                // fall through to other login flows
                log.debug("authorization header login attempt failed: {}", ignored.getMessage());
            }
        }

        // 2) Try refresh token cookie
        if (refreshToken != null) {
            try {
                if (jwtService.isRefreshToken(refreshToken) && jwtService.validateToken(refreshToken)) {
                    String oldJti = jwtService.extractJti(refreshToken);
                    if (refreshTokenService.validateRefreshToken(oldJti)) {
                        Long userId = jwtService.extractUserId(refreshToken);
                        String username = userService.getUsernameById(userId);
                        var newPair = jwtService.createTokenPair(userId, username);
                        String newRefreshJti = jwtService.extractJti(newPair.refreshToken());
                        boolean rotated = refreshTokenService.rotateRefreshTokenAtomic(oldJti, newRefreshJti, userId, jwtService.getRefreshExpirationMillis());
                        if (!rotated) {
                            failUserInfoDTO.setUsername("Refresh token 无效或已被使用");
                            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode())
                                    .body(Result.fail(ResultCode.UNAUTHORIZED, failUserInfoDTO));
                        }
                        JwtCookieUtil.writeRefreshCookie(response, newPair.refreshToken(), jwtService, jwtProperties);
                        response.setHeader("New-Access-Token", newPair.accessToken());
                        userService.onLoginSuccess(userId);

                        UserInfoDTO dto = buildUserInfo.apply(userId);
                        return ResponseEntity.ok(Result.success(dto));
                    }
                }
            } catch (Exception e) {
                failUserInfoDTO.setUsername("Refresh token 解析/旋转失败");
                return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode())
                        .body(Result.fail(ResultCode.UNAUTHORIZED, failUserInfoDTO));
            }
        }

        // 3) Credential login (username/password)
        if (loginDTO == null) {
            failUserInfoDTO.setUsername("需要凭证登录");
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, failUserInfoDTO));
        }
        Long userId = userService.authenticateAndGetId(loginDTO.getUsernameOrEmail(), loginDTO.getPassword());
        if (userId == null) {
            failUserInfoDTO.setUsername("凭证无效，登录失败");
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(Result.fail(ResultCode.UNAUTHORIZED, failUserInfoDTO));
        }

        String username = userService.getUsernameById(userId);
        var pair = jwtService.createTokenPair(userId, username);
        String refreshJti = jwtService.extractJti(pair.refreshToken());
        refreshTokenService.storeRefreshToken(refreshJti, userId, jwtService.getRefreshExpirationMillis());
        JwtCookieUtil.writeRefreshCookie(response, pair.refreshToken(), jwtService, jwtProperties);
        response.setHeader("New-Access-Token", pair.accessToken());
        userService.onLoginSuccess(userId);

        UserInfoDTO dto = buildUserInfo.apply(userId);
        return ResponseEntity.ok(Result.success(dto));
    }

    @Operation(summary = "注册新用户", description = "注册并立即登录（返回 access token 到 header，refresh 写入 HttpOnly cookie）")
    @PostMapping("/register")
    public ResponseEntity<Result<String>> register(@RequestBody UserRegisterDTO registerDTO,
                                                   HttpServletResponse response) {
        if (registerDTO == null) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "注册信息不能为空"));
        }
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "两次密码不一致"));
        }
        try {
            Long userId = userService.registerUser(registerDTO.getUsername(), registerDTO.getEmail(), registerDTO.getPassword());
            String username = userService.getUsernameById(userId);
            var pair = jwtService.createTokenPair(userId, username);
            String refreshJti = jwtService.extractJti(pair.refreshToken());
            refreshTokenService.storeRefreshToken(refreshJti, userId, jwtService.getRefreshExpirationMillis());
            JwtCookieUtil.writeRefreshCookie(response, pair.refreshToken(), jwtService, jwtProperties);
            response.setHeader("New-Access-Token", pair.accessToken());
            userService.onLoginSuccess(userId);
            return ResponseEntity.ok(Result.success("注册并登录成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body(Result.fail(ResultCode.SYSTEM_ERROR, "注册失败"));
        }
    }

    @Operation(summary = "登出", description = "清除 refresh cookie 并撤销 refresh token 与 access token（若存在）")
    @PostMapping("/logout")
    public ResponseEntity<Result<String>> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @CurrentUser Long currentUserId,
            HttpServletResponse response) {

        // 1) 如果有 refresh cookie，撤销对应 refresh token（通过 RefreshTokenService）
        if (refreshToken != null) {
            try {
                String jti = jwtService.extractJti(refreshToken);
                refreshTokenService.revokeRefreshToken(jti);
            } catch (Exception ignored) {
                log.debug("logout: failed to revoke refreshToken: {}", ignored.getMessage());
            }
        }

        // 2) 如果有 Authorization header，尝试撤销 access token（把 jti 写入 access blacklist）
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String accessToken = authorizationHeader.substring("Bearer ".length()).trim();
            try {
                String jti = jwtService.extractJti(accessToken);
                long remainingMillis = jwtService.getRemainingMillis(accessToken);
                if (remainingMillis > 0) {
                    accessTokenBlacklistService.blacklist(jti, remainingMillis);
                } else {
                    accessTokenBlacklistService.blacklist(jti, 60_000L); // fallback 1 minute
                }
            } catch (Exception ex) {
                log.debug("logout: failed to blacklist access token: {}", ex.getMessage());
            }
        }

        // 3) 清除 refresh cookie（HttpOnly cookie）
        JwtCookieUtil.clearRefreshCookie(response, jwtProperties);

        // 4) 清 SecurityContext（如果当前线程已认证）
        try {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        } catch (Exception ignore) {}

        return ResponseEntity.ok(Result.success("已登出"));
    }

    @Operation(summary = "更新个人签名", description = "修改当前登录用户的个人签名（长度上限 200）")
    @PostMapping("/signature")
    public ResponseEntity<Result<String>> updateSignature(@CurrentUser Long userId,
                                                          @RequestBody PersonalSignatureDTO dto) {
        if (userId == null) {
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode())
                    .body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
        }
        if (dto == null) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "请求体不能为空"));
        }
        String sig = dto.getPersonalSignature();
        if (sig == null) sig = "";
        if (sig.length() > 200) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "personalSignature length must <= 200"));
        }
        try {
            boolean ok = userService.updatePersonalSignature(userId, sig);
            if (ok) {
                return ResponseEntity.ok(Result.success("更新签名成功"));
            } else {
                return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body(Result.fail(ResultCode.SYSTEM_ERROR, "更新失败"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, e.getMessage()));
        } catch (Exception e) {
            log.error("updateSignature error userId={} sig={}", userId, sig, e);
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body(Result.fail(ResultCode.SYSTEM_ERROR, "更新签名失败"));
        }
    }

    @Operation(summary = "获取 presign（用户上传头像 专用）", description = "生成 presigned PUT URL，供前端上传用户头像")
    @PostMapping("/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequestDTO req, @CurrentUser Long userId) {
        String storagePath = "/userAvatar/" + userId + "/"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
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

    @Operation(summary = "获取当前登录用户信息", description = "根据 access token / refresh cookie 中的用户标识，返回 UserInfoDTO（包含头像 URL、签名等）")
    @GetMapping("/me")
    public ResponseEntity<Result<UserInfoDTO>> getCurrentUser(@CurrentUser Long currentUserId) {
        if (currentUserId == null) {
            failUserInfoDTO.setUsername("未授权");
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode())
                    .body(Result.fail(ResultCode.UNAUTHORIZED, failUserInfoDTO));
        }

        try {
            // 先通过 UserService 获取基础信息
            UserInfoDTO dto = new UserInfoDTO();
            dto.setId(String.valueOf(currentUserId));
            try {
                dto.setUsername(userService.getUsernameById(currentUserId));
            } catch (Exception ignored) {}

            // avatar
            try {
                Long avatarAttachmentId = userService.getAvatarAttachmentId(currentUserId);
                if (avatarAttachmentId != null) {
                    try {
                        String avatarUrl = attachmentService.generatePresignedGetUrl(avatarAttachmentId, 300L);
                        dto.setUserAvatarUrl(avatarUrl);
                    } catch (Exception e) {
                        log.debug("getCurrentUser: presigned url failed for avatar {}: {}", avatarAttachmentId, e.getMessage());
                        dto.setUserAvatarUrl(null);
                    }
                }
            } catch (Exception ignored) {}

            // signature
            try {
                dto.setPersonalSignature(userService.getPersonalSignature(currentUserId));
            } catch (Exception ignored) {}

            return ResponseEntity.ok(Result.success(dto));
        } catch (Exception e) {
            log.error("getCurrentUser error userId={}", currentUserId, e);
            failUserInfoDTO.setUsername("获取用户信息失败");
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode())
                    .body(Result.fail(ResultCode.SYSTEM_ERROR, failUserInfoDTO));
        }
    }

    @Operation(summary = "上传用户头像绑定", description = "把已上传的 attachmentId 绑定为当前用户的头像（需登录）")
    @PostMapping("/userAvatar")
    public ResponseEntity<?> postUserAvatar(@CurrentUser Long userId,
                                            @RequestBody AvatarBindDTO req) {
        if (userId == null) {
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(Result.fail(ResultCode.UNAUTHORIZED, "未授权"));
        }
        if (req == null || req.getAttachmentId() == null) {
            return ResponseEntity.status(ResultCode.BAD_REQUEST.getCode()).body(Result.fail(ResultCode.BAD_REQUEST, "attachmentId is required"));
        }

        Long attachmentId = req.getAttachmentId();

        // 1) 检查 attachment 是否存在
        com.anime.common.entity.attachment.Attachment a;
        try {
            a = attachmentService.getAttachmentById(attachmentId);
        } catch (Exception ex) {
            log.warn("postUserAvatar: failed to query attachment {}: {}", attachmentId, ex.getMessage());
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body(Result.fail(ResultCode.SYSTEM_ERROR, "附件查询失败"));
        }
        if (a == null) {
            return ResponseEntity.status(ResultCode.BAD_REQUEST.getCode()).body(Result.fail(ResultCode.BAD_REQUEST, "attachment not found"));
        }

        // 2) 校验归属（防止把别人的附件绑定为自己的头像）
        Long uploadedBy = a.getUploadedBy();
        if (uploadedBy == null || !uploadedBy.equals(userId)) {
            // 如果业务允许管理员等特殊角色可绑定他人附件，可在此添加额外判断
            return ResponseEntity.status(ResultCode.FORBIDDEN.getCode()).body(Result.fail(ResultCode.FORBIDDEN, "attachment not owned by user"));
        }

        // 3) 校验 attachment 状态（可按业务调整为只允许 status == "available"）
        String status = a.getStatus();
        if (status == null || !(status.equalsIgnoreCase("available") || status.equalsIgnoreCase("uploading"))) {
            return ResponseEntity.status(ResultCode.BAD_REQUEST.getCode()).body(Result.fail(ResultCode.BAD_REQUEST, "attachment status not valid for binding"));
        }

        // 4) 调用 service 更新用户 avatar（当前实现 userService.PostUserAvatar 会写入 DB）
        try {
            Long lastAttachmentId = userService.getAvatarAttachmentId(userId);
            Boolean ok = userService.PostUserAvatar(userId, attachmentId);
            if (Boolean.TRUE.equals(ok)) {
                // 返回新的 avatar presigned url 给前端做确认/预览（可选）
                String avatarUrl = null;
                try {
                    avatarUrl = attachmentService.generatePresignedGetUrl(attachmentId, 300L);
                } catch (Exception e) {
                    log.debug("postUserAvatar: presigned-get failed for {}: {}", attachmentId, e.getMessage());
                }
                try {
                    attachmentService.deleteAttachment(lastAttachmentId, false);
                } catch (Exception ex) {
                    log.warn("postUserAvatar: failed to delete last attachment {}: {}", attachmentId, ex.getMessage());
                }
                java.util.Map<String, Object> resp = new java.util.HashMap<>();
                resp.put("attachmentId", attachmentId);
                resp.put("avatarUrl", avatarUrl);
                return ResponseEntity.ok(Result.success(resp));
            } else {
                return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body(Result.fail(ResultCode.SYSTEM_ERROR, "绑定失败"));
            }
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(ResultCode.BAD_REQUEST.getCode()).body(Result.fail(ResultCode.BAD_REQUEST, iae.getMessage()));
        } catch (Exception ex) {
            log.error("postUserAvatar failed userId={} attachmentId={} : {}", userId, attachmentId, ex.getMessage(), ex);
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body(Result.fail(ResultCode.SYSTEM_ERROR, "绑定失败"));
        }
    }

    @Operation(summary = "ping", description = "用于心跳/测试（返回 pong）")
    @PostMapping("/ping")
    public ResponseEntity<Result<String>> ping() {
        return ResponseEntity.ok(Result.success("pong"));
    }

    @Operation(summary = "ping", description = "用于心跳/测试（返回 pong）")
    @PostMapping("/ping1")
    public ResponseEntity<Result<String>> ping1() {
        return ResponseEntity.ok(Result.success("pong"));
    }
}