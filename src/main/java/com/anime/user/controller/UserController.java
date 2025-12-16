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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 登录/注册接口（优化后）
 *
 * 修改点：注入 JwtProperties，并在写/清除 refresh cookie 时传入配置以控制 SameSite/Secure。
 */
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

    @PostMapping("/login")
    public ResponseEntity<Result<String>> login(
            @RequestBody(required = false) UserLoginDTO loginDTO,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        // 1) 优先检查 Authorization header（Bearer <token>）
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

        // 2) 尝试用 refreshToken cookie 刷新（如果有）
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

                        // 把新的 refresh 写入 HttpOnly cookie（传入 jwtProperties）
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

        // 3) 凭证登录（当没有有效 access，也无法用 refresh 刷新时）
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

        // 将 refresh token 写入 HttpOnly cookie，并把 access 放到响应 header 供前端立即同步
        JwtCookieUtil.writeRefreshCookie(response, pair.refreshToken(), jwtService, jwtProperties);
        response.setHeader("New-Access-Token", pair.accessToken());

        userService.onLoginSuccess(userId);
        return ResponseEntity.ok(Result.success("登录成功"));
    }

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

    @PostMapping("/logout")
    public ResponseEntity<Result<String>> logout(@CookieValue(value = "refreshToken", required = false) String refreshToken,
                                                 @CurrentUser Long currentUserId,
                                                 HttpServletResponse response) {
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

    @PostMapping("/ping")
    public ResponseEntity<Result<String>> ping(@CurrentUser Long currentUserId) {
        return ResponseEntity.ok(Result.success("pong"));
    }
}