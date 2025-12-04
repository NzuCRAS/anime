package com.anime.auth.filter;

import com.anime.auth.service.JwtService;
import com.anime.auth.utils.JwtCookieUtil;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 认证过滤器：优先检查 Authorization header，再检查 accessToken cookie，
 * access 无效则尝试从 refresh cookie 刷新。
 *
 * 注意：shouldSkipAuthentication 中列出的路径应与 SecurityConfig 中 permitAll 的路径保持一致，防止不必要的拦截/刷新。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    // 把免认证路径集中在一个可维护的位置（这里使用 List，简单明了）
    private static final List<String> SKIP_PATH_PREFIXES = List.of(
            "/api/user/login",
            "/api/user/register",
            "/api/user/ping",
            "/api/auth/refresh",
            "/api/test/",
            "/public/",
            "/static/",
            "/"
    );

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        if (shouldSkipAuthentication(requestPath) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 1) 先检查 Authorization header
            String accessToken = extractTokenFromRequest(request);

            // 2) 若 header 没有，则检查 accessToken cookie（支持两种策略）
            if (accessToken == null) {
                accessToken = extractCookieValue(request, "accessToken");
            }

            if (accessToken != null && jwtService.validateToken(accessToken) && jwtService.isAccessToken(accessToken)) {
                setAuthentication(accessToken);
                filterChain.doFilter(request, response);
                return;
            }

            // 3) AccessToken 无效/过期 -> 从 Cookie 获取 refreshToken
            String refreshToken = extractCookieValue(request, "refreshToken");
            if (refreshToken != null) {
                try {
                    JwtService.TokenPair newTokens = jwtService.refreshAccessToken(refreshToken);

                    // 写入新的 HttpOnly cookie（统一策略）
                    JwtCookieUtil.writeTokenCookies(response, newTokens, jwtService);

                    // 额外把新的 access 放 header 供前端同步（CORS 已 expose）
                    response.setHeader("New-Access-Token", newTokens.accessToken());

                    setAuthentication(newTokens.accessToken());
                    filterChain.doFilter(request, response);
                    return;
                } catch (Exception e) {
                    log.debug("refresh in filter failed: {}", e.getMessage());
                }
            }

            handleUnauthorized(response, "Token无效或已过期，请重新登录");

        } catch (Exception e) {
            log.error("JWT认证过程中发生错误: {}", e.getMessage());
            handleUnauthorized(response, "认证失败");
        }
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private String extractCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void setAuthentication(String accessToken) {
        Long userId = jwtService.extractUserId(accessToken);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private boolean shouldSkipAuthentication(String path) {
        if (path == null) return false;
        return SKIP_PATH_PREFIXES.stream().anyMatch(prefix -> path.startsWith(prefix));
    }

    private void handleUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<String> result = Result.fail(ResultCode.UNAUTHORIZED, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}