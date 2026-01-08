package com.anime.chat.socket;

import com.anime.common.dto.chat.socket.WebSocketEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class WsEventPublisher {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /**
     * 向某个用户推送任意类型的 WS 事件（会发到该用户所有在线 WebSocketSession）
     */
    public void sendToUser(Long userId, String type, Object payload) {
        if (userId == null) return;
        try {
            WebSocketEnvelope<Object> env = new WebSocketEnvelope<>();
            env.setType(type);
            env.setPayload(payload);
            String json = objectMapper.writeValueAsString(env);
            log.error("WsEventPublisher sendToUser userId={}, type={}, json={}",
                    userId, type, json);
            sessionManager.sendToUser(userId, new TextMessage(json));
        } catch (Exception e) {
            log.warn("WsEventPublisher sendToUser failed, userId={}, type={}, err={}",
                    userId, type, e.getMessage());
        }
    }
}