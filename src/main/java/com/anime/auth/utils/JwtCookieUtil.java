package com.anime.auth.utils;

import com.anime.auth.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * 仅设置 refresh token cookie（HttpOnly），不再把 accessToken 写入 HttpOnly cookie。
 * 前端应在 refresh 返回的 JSON 中取到 access token 并把它放到内存中（传递 Authorization header）。
 */
public class JwtCookieUtil {

    public static void writeRefreshCookie(HttpServletResponse response, String refreshToken, JwtService jwtService) {
        long refreshMaxAgeSec = Math.max(1, jwtService.getRefreshExpirationMillis() / 1000);

        // Compose Set-Cookie header with SameSite=Lax (or Strict depending on requirements)
        // 使用 ResponseCookie 构造后再通过 header 写入，确保 SameSite 被写入
        String setCookieHeader = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshMaxAgeSec)
                .sameSite("Lax")
                .build()
                .toString();

        response.addHeader(HttpHeaders.SET_COOKIE, setCookieHeader);
    }

    public static void clearRefreshCookie(HttpServletResponse response) {
        String clearCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build()
                .toString();
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie);
    }
}