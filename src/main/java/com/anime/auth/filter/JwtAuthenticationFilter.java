package com.anime.auth.filter;

import com.anime.auth.service.JwtService;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 精简后的认证过滤器：
 * - 仅从 Authorization header 中提取 access token（Bearer）
 * - 不再从 cookie 中读取 access token，也不在 filter 中自动用 refresh token 刷新
 * - 遇到无效或过期的 access token 返回 401，前端负责调用 refresh endpoint
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

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
            String accessToken = extractTokenFromRequest(request);

            if (accessToken != null && jwtService.validateToken(accessToken) && jwtService.isAccessToken(accessToken)) {
                setAuthentication(accessToken);
                filterChain.doFilter(request, response);
                return;
            }

            // 无有效 access token，直接返回 401；前端应在收到 401 时调用 /api/auth/refresh 或跳转登录
            handleUnauthorized(response, "Token invalid or expired; please refresh or re-login");
        } catch (Exception e) {
            log.error("Error during JWT authentication: {}", e.getMessage(), e);
            handleUnauthorized(response, "Authentication failure");
        }
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return null;
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