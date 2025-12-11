package com.anime.auth.controller;

import com.anime.auth.service.JwtService;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.anime.auth.utils.JwtCookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.anime.common.result.Result.fail;

/**
 * refresh endpoint:
 * - 从 HttpOnly refresh cookie 中读取 refresh token（浏览器会自动带上）
 * - 校验并旋转 refresh token（JwtService.refreshAccessToken 已实现 rotation）
 * - 写回新的 refresh cookie（HttpOnly）
 * - 在 response body 中返回新的 access token（前端把它保存在内存，并用于后续 Authorization header）
 *
 * 为防止 CSRF（refresh 依赖 cookie），建议做 Origin 检查或要求前端在请求头带自定义 header（并在此处校验）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    // allowedOrigin 可以从配置注入，示例中简化为硬编码或通过 properties 注入
    private final String allowedOrigin = "http://localhost:3000";

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/refresh")
    public ResponseEntity<Result<Map<String, String>>> refresh(HttpServletRequest request, HttpServletResponse response,
                                                               @RequestHeader(value = "Origin", required = false) String origin) {
        // Basic Origin check to reduce CSRF risk (production: read from config and validate)
        if (origin == null || !origin.equalsIgnoreCase(allowedOrigin)) {
            Map<String, String> result = new HashMap<>();
            result.put("message", "Invalid origin");
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(fail(ResultCode.UNAUTHORIZED, result));
        }

        String refreshToken = null;
        if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if ("refreshToken".equals(c.getName())) {
                    refreshToken = c.getValue();
                    break;
                }
            }
        }
        if (refreshToken == null) {
            Map<String, String> result = new HashMap<>();
            result.put("message", "Refresh token missing");
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(fail(ResultCode.UNAUTHORIZED, result));
        }
        try {
            var pair = jwtService.refreshAccessToken(refreshToken);
            // 写回 HttpOnly refresh cookie（只写 refresh）
            JwtCookieUtil.writeRefreshCookie(response, pair.refreshToken(), jwtService);
            // 返回新的 access token 到 body，前端将其存入内存（并在后续请求的 Authorization header 使用）
            Map<String, String> data = new HashMap<>();
            data.put("accessToken", pair.accessToken());
            return ResponseEntity.ok(Result.success(data));
        } catch (Exception e) {
            Map<String, String> result = new HashMap<>();
            result.put("message", "Refresh failed: " + e.getMessage());
            // 清除 cookie for safety
            JwtCookieUtil.clearRefreshCookie(response);
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(fail(ResultCode.UNAUTHORIZED, result));
        }
    }
}