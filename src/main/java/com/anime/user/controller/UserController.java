package com.anime.user.controller;

import com.anime.auth.service.JwtService;
import com.anime.auth.service.RefreshTokenService;
import com.anime.auth.utils.JwtCookieUtil;
import com.anime.common.dto.user.UserLoginDTO;
import com.anime.common.dto.user.UserRegisterDTO;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.anime.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 登录/注册接口（优先检查 accessToken -> refreshToken -> 凭证登录/注册）
 *
 * 注意：登录/注册成功会把 refresh 写入 HttpOnly cookie，并把 access 放到响应 header ("New-Access-Token")
 * 前端需在 axios 中读取该 header 并把 access 存入内存（或用于 Authorization header）
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    public UserController(JwtService jwtService,
                          RefreshTokenService refreshTokenService,
                          UserService userService) {
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
    }

    @CrossOrigin(origins = "https://localhost:5173")
    @PostMapping("/login")
    public ResponseEntity<Result<String>> login(
            @RequestBody(required = false) UserLoginDTO loginDTO,
            @CookieValue(value = "accessToken", required = false) String accessToken,
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        // 1) accessToken 有效 -> 直接返回登录成功，并把 accessToken 暴露到响应 header 供前端同步内存
        if (accessToken != null && jwtService.validateToken(accessToken) && jwtService.isAccessToken(accessToken)) {
            Long userId = jwtService.extractUserId(accessToken);
            userService.onLoginSuccess(userId);
            // 将现有 accessToken 放到 header，便于前端取回并同步内存
            response.setHeader("New-Access-Token", accessToken);
            return ResponseEntity.ok(Result.success("已登录(基于现有 accessToken)"));
        }

        // 2) 尝试用 refreshToken 刷新（如果有）
        if (refreshToken != null && jwtService.isRefreshToken(refreshToken) && jwtService.validateToken(refreshToken)) {
            String oldJti = jwtService.extractJti(refreshToken);
            if (refreshTokenService.validateRefreshToken(oldJti)) {
                Long userId = jwtService.extractUserId(refreshToken);
                String username = userService.getUsernameById(userId);

                // 生成新对 token
                var newPair = jwtService.createTokenPair(userId, username);
                String newRefreshJti = jwtService.extractJti(newPair.refreshToken());

                // 在 Redis 中原子旋转（删除旧 jti，写入新 jti）
                boolean rotated = refreshTokenService.rotateRefreshTokenAtomic(oldJti, newRefreshJti, userId, jwtService.getRefreshExpirationMillis());
                if (!rotated) {
                    // 旋转失败，要求重新登录
                    return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(Result.fail(ResultCode.UNAUTHORIZED, "Refresh token 无效或已被使用"));
                }

                // 把新的 token 写入 HttpOnly cookie（refresh）并把 access 放 header
                JwtCookieUtil.writeRefreshCookie(response, newPair.refreshToken(), jwtService);
                response.setHeader("New-Access-Token", newPair.accessToken());

                userService.onLoginSuccess(userId);
                return ResponseEntity.ok(Result.success("通过 refresh 自动登录"));
            }
        }

        // 3) 无效的 refresh -> 必须进行凭证登录
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

        // 在 Redis 存储 refresh jti
        refreshTokenService.storeRefreshToken(refreshJti, userId, jwtService.getRefreshExpirationMillis());

        // 将 token 写入 cookie，并把 access 放到响应 header 供前端立即同步
        JwtCookieUtil.writeRefreshCookie(response, pair.refreshToken(), jwtService);
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
            // 创建用户（会抛异常如果用户名/邮箱重复）
            Long userId = userService.registerUser(registerDTO.getUsername(), registerDTO.getEmail(), registerDTO.getPassword());

            // 注册后视为已登录：生成 token 对并写 cookie，存 redis
            String username = userService.getUsernameById(userId);
            var pair = jwtService.createTokenPair(userId, username);
            String refreshJti = jwtService.extractJti(pair.refreshToken());
            refreshTokenService.storeRefreshToken(refreshJti, userId, jwtService.getRefreshExpirationMillis());
            JwtCookieUtil.writeRefreshCookie(response, pair.refreshToken(), jwtService);

            // 把 access 暴露到 header 供前端立即使用
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
                                                 HttpServletResponse response) {
        if (refreshToken != null) {
            try {
                String jti = jwtService.extractJti(refreshToken);
                refreshTokenService.revokeRefreshToken(jti);
            } catch (Exception ignored) {}
        }
        JwtCookieUtil.clearRefreshCookie(response);
        return ResponseEntity.ok(Result.success("已登出"));
    }

    @PostMapping("/ping")
    public ResponseEntity<Result<String>> ping() {
        return ResponseEntity.ok(Result.success("pong"));
    }
}