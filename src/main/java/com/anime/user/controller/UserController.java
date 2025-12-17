package com.anime.user.controller;

import com.anime.auth.service.JwtService;
import com.anime.auth.service.RefreshTokenService;
import com.anime.auth.utils.JwtCookieUtil;
import com.anime.auth.web.CurrentUser;
import com.anime.common.dto.user.UserLoginDTO;
import com.anime.common.dto.user.UserRegisterDTO;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.anime.config.JwtProperties;
import com.anime.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 登录/注册接口（优化后）
 */
@Tag(name = "User", description = "用户登录 / 注册 / logout / ping 等接口")
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final JwtProperties jwtProperties;

    public UserController(JwtService jwtService,
                          RefreshTokenService refreshTokenService,
                          UserService userService,
                          JwtProperties jwtProperties) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
        this.jwtProperties = jwtProperties;
    }

    @Operation(summary = "登录（支持 Authorization header / refresh cookie / 凭证三种方式）", description = "优先使用 Authorization header；其次尝试 refresh cookie；否则使用用户名密码登录")
    @PostMapping("/login")
    public ResponseEntity<Result<String>> login(
            @RequestBody(required = false) UserLoginDTO loginDTO,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        // 原实现不变...
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
        // 原实现不变...
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

    @Operation(summary = "登出", description = "清除 refresh cookie 并撤销 refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Result<String>> logout(@CookieValue(value = "refreshToken", required = false) String refreshToken,
                                                 @CurrentUser Long currentUserId,
                                                 HttpServletResponse response) {
        // 原实现不变...
        if (refreshToken != null) {
            try {
                String jti = jwtService.extractJti(refreshToken);
                refreshTokenService.revokeRefreshToken(jti);
            } catch (Exception ignored) {
            }
        }
        JwtCookieUtil.clearRefreshCookie(response, jwtProperties);
        return ResponseEntity.ok(Result.success("已登出"));
    }

    @Operation(summary = "ping", description = "用于心跳/测试 auth（返回 pong）")
    @PostMapping("/ping")
    public ResponseEntity<Result<String>> ping(@CurrentUser Long currentUserId) {
        return ResponseEntity.ok(Result.success("pong"));
    }
}