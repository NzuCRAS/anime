package com.anime.auth.utils;

import com.anime.auth.service.JwtService;
import com.anime.config.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * JwtCookieUtil - 根据 JwtProperties 写入或清除 refresh cookie
 *
 * 注意：调用方需传入 JwtProperties，使得 dev/prod 配置可控。
 */
public class JwtCookieUtil {

    public static void writeRefreshCookie(HttpServletResponse response,
                                          String refreshToken,
                                          JwtService jwtService,
                                          JwtProperties jwtProperties) {
        long refreshMaxAgeSec = Math.max(1, jwtService.getRefreshExpirationMillis() / 1000);

        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .path("/")
                .maxAge(refreshMaxAgeSec);

        // sameSite 配置
        String sameSite = (jwtProperties != null && jwtProperties.getCookieSameSite() != null)
                ? jwtProperties.getCookieSameSite()
                : "Lax";
        builder.sameSite(sameSite);

        // secure 配置
        boolean secure = jwtProperties != null && jwtProperties.isCookieSecure();
        builder.secure(secure);

        String setCookieHeader = builder.build().toString();
        response.addHeader(HttpHeaders.SET_COOKIE, setCookieHeader);
    }

    public static void clearRefreshCookie(HttpServletResponse response, JwtProperties jwtProperties) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0);

        String sameSite = (jwtProperties != null && jwtProperties.getCookieSameSite() != null)
                ? jwtProperties.getCookieSameSite()
                : "Lax";
        builder.sameSite(sameSite);

        boolean secure = jwtProperties != null && jwtProperties.isCookieSecure();
        builder.secure(secure);

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}