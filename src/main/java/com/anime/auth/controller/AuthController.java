package com.anime.auth.controller;

import com.anime.auth.service.JwtService;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.anime.auth.utils.JwtCookieUtil;
import com.anime.config.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Refresh endpoint - 改进后的实现（注入 JwtProperties，用于写入 SameSite/Secure cookie）
 */
@Tag(name = "Auth", description = "认证相关（refresh token）")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final Environment env;
    private final JwtProperties jwtProperties;

    public AuthController(JwtService jwtService, Environment env, JwtProperties jwtProperties) {
        this.jwtService = jwtService;
        this.env = env;
        this.jwtProperties = jwtProperties;
    }

    @Operation(summary = "刷新 access token（使用 refresh cookie）", description = "从 HttpOnly refresh cookie 中读取 refresh token，并返回新的 access token（并刷新 cookie）")
    @PostMapping("/refresh")
    public ResponseEntity<Result<Map<String, String>>> refresh(HttpServletRequest request,
                                                               HttpServletResponse response,
                                                               @RequestHeader(value = "Origin", required = false) String origin,
                                                               @RequestHeader(value = "X-Requested-With", required = false) String xRequestedWith) {
        String[] configured = env.getProperty("cors.allowed-origins", String[].class);
        Set<String> allowed = new HashSet<>();
        if (configured != null) {
            for (String s : configured) {
                if (s != null && !s.isBlank()) {
                    allowed.add(s.trim().toLowerCase());
                }
            }
        }
        System.out.println("[AuthController] refresh called. Origin=" + origin + " allowed=" + allowed + " X-Requested-With=" + xRequestedWith);
        boolean originOk = false;
        if (origin != null && !origin.isBlank()) {
            originOk = allowed.contains(origin.trim().toLowerCase());
        }
        boolean headerOk = xRequestedWith != null && !xRequestedWith.isBlank();
        if (!originOk && !headerOk) {
            Map<String, String> result = new HashMap<>();
            result.put("message", "Invalid origin or missing required header");
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(Result.fail(ResultCode.UNAUTHORIZED, result));
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
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(Result.fail(ResultCode.UNAUTHORIZED, result));
        }
        try {
            var pair = jwtService.refreshAccessToken(refreshToken);
            JwtCookieUtil.writeRefreshCookie(response, pair.refreshToken(), jwtService, jwtProperties);
            Map<String, String> data = new HashMap<>();
            data.put("accessToken", pair.accessToken());
            return ResponseEntity.ok(Result.success(data));
        } catch (Exception e) {
            Map<String, String> result = new HashMap<>();
            result.put("message", "Refresh failed: " + e.getMessage());
            JwtCookieUtil.clearRefreshCookie(response, jwtProperties);
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body(Result.fail(ResultCode.UNAUTHORIZED, result));
        }
    }
}