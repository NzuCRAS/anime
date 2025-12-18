package com.anime.auth.filter;

import com.anime.auth.service.AccessTokenBlacklistService;
import com.anime.auth.service.JwtService;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final AccessTokenBlacklistService accessTokenBlacklistService;

    private static final List<String> SKIP_PATH_PATTERNS = List.of(
            "/api/user/login",
            "/api/user/register",
            "/api/user/ping",
            "/api/auth/refresh",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/swagger-ui-dist/**",
            "/swagger-resources/**",
            "/webjars/**",
            "/public/**",
            "/static/**",
            "/api/test/**",
            "/api/user/logout"
    );

    private static final Set<String> STATIC_EXT_WHITELIST = Set.of(
            ".css", ".js", ".map", ".png", ".jpg", ".jpeg", ".gif", ".webp",
            ".svg", ".ico", ".woff", ".woff2", ".ttf", ".eot", ".html"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String accept = request.getHeader("Accept");
        String secFetchDest = request.getHeader("Sec-Fetch-Dest");

        log.debug("JwtAuthenticationFilter: incoming path={}, method={}, Accept={}, Sec-Fetch-Dest={}",
                requestPath, request.getMethod(), accept, secFetchDest);

        // 1) 基于路径 pattern 跳过
        if (shouldSkipAuthentication(requestPath)) {
            log.debug("JwtAuthenticationFilter: skipping authentication by pattern for path={}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        // 2) 静态资源判断跳过
        if (isStaticResource(requestPath) ||
                (accept != null && (accept.contains("text/css") || accept.contains("application/javascript") || accept.contains("image/"))) ||
                (secFetchDest != null && (secFetchDest.equalsIgnoreCase("style")
                        || secFetchDest.equalsIgnoreCase("script")
                        || secFetchDest.equalsIgnoreCase("image")))) {
            log.debug("JwtAuthenticationFilter: skipping authentication by static/resource heuristics for path={}, Accept={}, SecFetchDest={}", requestPath, accept, secFetchDest);
            filterChain.doFilter(request, response);
            return;
        }

        // 3) token 逻辑
        String accessToken = extractTokenFromRequest(request);
        log.debug("JwtAuthenticationFilter: hasAuthorizationHeader={}", accessToken != null);

        if (accessToken != null) {
            try {
                boolean valid = jwtService.validateToken(accessToken) && jwtService.isAccessToken(accessToken);
                log.debug("JwtAuthenticationFilter: token validation result={}", valid);
                // if token is valid, check jti blacklist first
                if (valid) {
                    String jti = jwtService.extractJti(accessToken);
                    if (accessTokenBlacklistService.isBlacklisted(jti)) {
                        log.warn("JwtAuthenticationFilter: access token jti={} is blacklisted", jti);
                        handleUnauthorized(response, "Token revoked");
                        return;
                    }
                    Long userId = jwtService.extractUserId(accessToken);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.info("JwtAuthenticationFilter: authenticated userId={} for path={}", userId, requestPath);
                    filterChain.doFilter(request, response);
                    return;
                } else {
                    log.warn("JwtAuthenticationFilter: token invalid or expired for path={}", requestPath);
                    handleUnauthorized(response, "Token invalid or expired; please refresh or re-login");
                    return;
                }
            } catch (Exception ex) {
                log.error("JwtAuthenticationFilter: exception validating token for path=" + requestPath, ex);
                handleUnauthorized(response, "Authentication failure");
                return;
            }
        }

        // 没有 Authorization header -> 继续 filterChain，由 Spring Security 决定 permitAll/AuthenticationEntryPoint
        log.debug("JwtAuthenticationFilter: no Authorization header, continue filter chain for path={}", requestPath);
        filterChain.doFilter(request, response);
    }

    private boolean shouldSkipAuthentication(String path) {
        if (path == null) return false;
        for (String pattern : SKIP_PATH_PATTERNS) {
            if (pathMatcher.match(pattern, path)) return true;
        }
        return false;
    }

    private boolean isStaticResource(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        for (String ext : STATIC_EXT_WHITELIST) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return null;
    }

    private void handleUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<String> result = Result.fail(ResultCode.UNAUTHORIZED, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}