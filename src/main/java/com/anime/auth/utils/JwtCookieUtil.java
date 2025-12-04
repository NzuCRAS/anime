package com.anime.auth.utils;

import com.anime.auth.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * 辅助方法：把 token 写进 HttpOnly cookie / 清除 cookie
 * 注意：生产环境请把 secure=true，并根据域名/路径做好配置
 */
public class JwtCookieUtil {

    public static void writeTokenCookies(HttpServletResponse response, JwtService.TokenPair tokens, JwtService jwtService) {
        long accessMaxAgeSec = Math.max(1, jwtService.getAccessExpirationMillis() / 1000);
        long refreshMaxAgeSec = Math.max(1, jwtService.getRefreshExpirationMillis() / 1000);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.accessToken())
                .httpOnly(true)
                .secure(false) // 生产环境请改为 true
                .path("/")
                .maxAge(accessMaxAgeSec)
                .sameSite("Lax")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.refreshToken())
                .httpOnly(true)
                .secure(false) // 生产环境请改为 true
                .path("/")
                .maxAge(refreshMaxAgeSec)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public static void clearTokenCookies(HttpServletResponse response) {
        ResponseCookie clearAccess = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(false) // 生产环境请改为 true
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        ResponseCookie clearRefresh = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false) // 生产环境请改为 true
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, clearAccess.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh.toString());
    }
}