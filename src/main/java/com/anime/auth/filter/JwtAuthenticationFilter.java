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
/*            "/api/attachments/presign",
            "/api/attachments/complete",
            "/api/test/",*/
            "/public/",
            "/static/"
    );

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

// 在 JwtAuthenticationFilter.doFilterInternal 中使用此调试片段（替换相应逻辑）
        String requestPath = request.getRequestURI();
        log.debug("JwtAuthenticationFilter: incoming path={}, method={}", requestPath, request.getMethod());

        if (shouldSkipAuthentication(requestPath) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            log.debug("JwtAuthenticationFilter: skipping authentication for path={}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = extractTokenFromRequest(request);
        log.debug("JwtAuthenticationFilter: hasAuthorizationHeader={}", accessToken != null);

        if (accessToken != null) {
            try {
                boolean valid = jwtService.validateToken(accessToken) && jwtService.isAccessToken(accessToken);
                log.debug("JwtAuthenticationFilter: token validation result={}", valid);
                if (valid) {
                    Long userId = jwtService.extractUserId(accessToken);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.info("JwtAuthenticationFilter: authenticated userId={} for path={}", userId, requestPath);
                    filterChain.doFilter(request, response);
                    return;
                } else {
                    log.warn("JwtAuthenticationFilter: token invalid or not access token for path={}", requestPath);
                }
            } catch (Exception ex) {
                log.error("JwtAuthenticationFilter: exception while validating token for path=" + requestPath, ex);
                // 返回 401 JSON
                handleUnauthorized(response, "Authentication failure");
                return;
            }
        }

// 若没有有效 token
        log.debug("JwtAuthenticationFilter: no valid token for path={}", requestPath);
        handleUnauthorized(response, "Token invalid or expired; please refresh or re-login");
    }

    /**
     * 从Authorization Headers中获取accessToken
     * @param request
     * @return
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return null;
    }

    private void setAuthentication(String accessToken, String requestPath) {
        Long userId = jwtService.extractUserId(accessToken);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.debug("JwtAuthenticationFilter: authenticated userId={} for path={}", userId, requestPath);
    }

    private boolean shouldSkipAuthentication(String path) {
        if (path == null) return false;
        return SKIP_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void handleUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<String> result = Result.fail(ResultCode.UNAUTHORIZED, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}