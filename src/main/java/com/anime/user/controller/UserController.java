package com.anime.user.controller;

import com.anime.auth.service.AccessTokenBlacklistService;
import com.anime.auth.service.JwtService;
import com.anime.auth.service.RefreshTokenService;
import com.anime.auth.utils.JwtCookieUtil;
import com.anime.auth.web.CurrentUser;
import com.anime.common.dto.attachment.PresignRequestDTO;
import com.anime.common.dto.attachment.PresignResponseDTO;
import com.anime.common.dto.user.UserLoginDTO;
import com.anime.common.dto.user.UserRegisterDTO;
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

    @Operation(summary = "登录（支持 Authorization header / refresh cookie / 凭证三种方式）", description = "优先使用 Authorization header；其次尝试 refresh cookie；否则使用用户名密码登录")
    @PostMapping("/login")
    public ResponseEntity<Result<String>> login(
            @RequestBody(required = false) UserLoginDTO loginDTO,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String accessToken = authorizationHeader.substring("Bearer ".length()).trim();
            try {
                if (jwtService.validateToken(accessToken) && jwtService.isAccessToken(accessToken)) {
                    Long userId = jwtService.extractUserId(accessToken);
                    userService.onLoginSuccess(userId);
                    response.setHeader("New-Access-Token", accessToken);
                    return ResponseEntity.ok(Result.success("已登录(基于 Authorization header)"));
                }
            } catch (Exception ignored) {}
        }
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
                            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode())
                                    .body(Result.fail(ResultCode.UNAUTHORIZED, "Refresh token 无效或已被使用"));
                        }
                        JwtCookieUtil.writeRefreshCookie(response, newPair.refreshToken(), jwtService, jwtProperties);
                        response.setHeader("New-Access-Token", newPair.accessToken());
                        userService.onLoginSuccess(userId);
                        return ResponseEntity.ok(Result.success("通过 refresh 自动登录"));
                    }
                }
            } catch (Exception e) {
                return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode())
                        .body(Result.fail(ResultCode.UNAUTHORIZED, "Refresh token 解析/旋转失败"));
            }
        }
        if (loginDTO == null) {
            return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, "需要凭证登录"));
        }
        Long userId = userService.authenticateAndGetId(loginDTO.getUsernameOrEmail(), loginDTO.getPassword());
        if (userId == null) {
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(Result.fail(ResultCode.UNAUTHORIZED, "凭证无效，登录失败"));
        }
        String username = userService.getUsernameById(userId);
        var pair = jwtService.createTokenPair(userId, username);
        String refreshJti = jwtService.extractJti(pair.refreshToken());
        refreshTokenService.storeRefreshToken(refreshJti, userId, jwtService.getRefreshExpirationMillis());
        JwtCookieUtil.writeRefreshCookie(response, pair.refreshToken(), jwtService, jwtProperties);
        response.setHeader("New-Access-Token", pair.accessToken());
        userService.onLoginSuccess(userId);
        return ResponseEntity.ok(Result.success("登录成功"));
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

    @Operation(summary = "上传用户头像" , description = "拿取用户id和attachmentId,绑定attachment和用户头像")
    @PostMapping("/userAvatar")
    public ResponseEntity<?> postUserAvatar(@CurrentUser Long userId, Long attachmentId) {
        if (attachmentId == null) {
            return ResponseEntity.status(ResultCode.BAD_REQUEST.getCode()).body("attachmentId is null");
        }
        if (postUserAvatar(userId, attachmentId) == null) {
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body("postUserAvatar failed");
        }
        return ResponseEntity.status(ResultCode.SUCCESS.getCode()).body("success post userAvatar");
    }

    @Operation(summary = "ping", description = "用于心跳/测试 auth（返回 pong）")
    @PostMapping("/ping")
    public ResponseEntity<Result<String>> ping(@CurrentUser Long currentUserId) {
        return ResponseEntity.ok(Result.success("pong"));
    }
}