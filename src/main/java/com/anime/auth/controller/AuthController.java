package com.anime.auth.controller;

import com.anime.auth.service.JwtService;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.anime.auth.utils.JwtCookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(HttpServletRequest request, HttpServletResponse response) {
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
            return Result.fail(ResultCode.UNAUTHORIZED,result);
        }
        try {
            var pair = jwtService.refreshAccessToken(refreshToken);
            // 写回 HttpOnly cookie（refresh）
            JwtCookieUtil.writeTokenCookies(response, pair, jwtService);
            // 同时把 access 也放入返回 body，方便前端把 access 存入内存
            Map<String, String> data = new HashMap<>();
            data.put("accessToken", pair.accessToken());
            return Result.success(data);
        } catch (Exception e) {
            Map<String, String> result = new HashMap<>();
            result.put("message", "Refresh failed: " + e.getMessage());
            return Result.fail(ResultCode.UNAUTHORIZED ,result);
        }
    }
}