package com.anime.chat.socket;

import com.anime.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器：
 * 从 Authorization 头或 query 参数中解析用户ID，并放到 attributes 中。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        Long userId = null;

        if (request instanceof ServletServerHttpRequest servletRequest) {
            var http = servletRequest.getServletRequest();

            // 1. 尝试从 Authorization Header 取 Bearer Token
            String authHeader = http.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring("Bearer ".length()).trim();
                try {
                    if (jwtService.validateToken(token) && jwtService.isAccessToken(token)) {
                        userId = jwtService.extractUserId(token);
                    }
                } catch (Exception e) {
                    log.warn("WS handshake: invalid Authorization token: {}", e.getMessage());
                }
            }

            // 2. 也可以支持从 query 参数 token=xxx 来传（看你前端方便程度）
            if (userId == null) {
                String token = http.getParameter("token");
                if (token != null && !token.isBlank()) {
                    try {
                        if (jwtService.validateToken(token) && jwtService.isAccessToken(token)) {
                            userId = jwtService.extractUserId(token);
                        }
                    } catch (Exception e) {
                        log.warn("WS handshake: invalid token parameter: {}", e.getMessage());
                    }
                }
            }
        }

        if (userId == null) {
            log.warn("WS handshake rejected: userId not resolved");
            return false;
        }

        // 将 userId 放入 WebSocketSession attributes 中，后面 handler 可以获取
        attributes.put("userId", userId);
        log.info("WS handshake success, userId={}", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 无需额外处理
    }
}
