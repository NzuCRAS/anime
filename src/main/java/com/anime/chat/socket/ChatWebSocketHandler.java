package com.anime.chat.socket;

import com.anime.chat.service.ChatMessageService;
import com.anime.common.dto.chat.socket.NewMessageResponse;
import com.anime.common.dto.chat.socket.SendMessageRequest;
import com.anime.common.dto.chat.socket.WebSocketEnvelope;
import com.anime.common.entity.chat.ChatMessage;
import com.anime.common.mapper.chat.ChatGroupMemberMapper;
import com.anime.common.service.AttachmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 处理器：负责接收消息、调用业务层、广播消息。
 *
 * 已修正：
 * - 私聊：为发送者与接收者分别构造并发送各自视角的 NewMessageResponse（确保 payload.toUserId 对应接收者）
 * - 群聊：为每个群成员构造单独的 payload（toUserId = 该成员），并逐个发送（不再复用发送者视角用于所有接收者）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatMessageService chatMessageService;
    private final AttachmentService attachmentService;
    private final ChatGroupMemberMapper chatGroupMemberMapper;
    private final ObjectMapper objectMapper;

    // userId -> 多个 WebSocketSession
    private final Map<Long, java.util.Set<WebSocketSession>> onlineUsers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId == null) {
            log.warn("WS connection established but userId is null, closing");
            closeSession(session, CloseStatus.BAD_DATA);
            return;
        }
        onlineUsers.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WS connected: userId={} sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Long userId = getUserId(session);
        if (userId == null) {
            log.warn("WS message but userId is null, closing");
            closeSession(session, CloseStatus.BAD_DATA);
            return;
        }

        String payload = message.getPayload();
        log.debug("WS recv from userId={} payload={}", userId, payload);

        WebSocketEnvelope<?> envelope;
        try {
            envelope = objectMapper.readValue(payload, WebSocketEnvelope.class);
        } catch (Exception e) {
            log.warn("WS parse envelope failed: {}", e.getMessage());
            return;
        }

        if ("SEND_MESSAGE".equalsIgnoreCase(envelope.getType())) {
            // 因为 ObjectMapper 反序列化成原始类型会丢掉泛型信息，
            // 这里再手动反一次 payload 部分。
            WebSocketEnvelope<SendMessageRequest> env =
                    objectMapper.readValue(payload,
                            objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, SendMessageRequest.class));
            handleSendMessage(session, userId, env.getPayload());
        } else {
            log.debug("WS unknown type: {}", envelope.getType());
        }
    }

    private void handleSendMessage(WebSocketSession session, Long fromUserId, SendMessageRequest req) {
        try {
            // 1. 基本校验
            String convType = req.getConversationType();
            String msgType = req.getMessageType();
            if (convType == null || msgType == null) {
                log.warn("WS SEND_MESSAGE missing conversationType or messageType, userId={}", fromUserId);
                return;
            }

            Long toUserId = null;
            Long groupId = null;
            if ("PRIVATE".equalsIgnoreCase(convType)) {
                toUserId = req.getTargetUserId();
                if (toUserId == null) {
                    log.warn("WS PRIVATE message missing targetUserId, fromUserId={}", fromUserId);
                    return;
                }
            } else if ("GROUP".equalsIgnoreCase(convType)) {
                groupId = req.getGroupId();
                if (groupId == null) {
                    log.warn("WS GROUP message missing groupId, fromUserId={}", fromUserId);
                    return;
                }
            } else {
                log.warn("WS invalid conversationType: {}", convType);
                return;
            }

            // 2. 调用业务层保存消息（saveMessage 会为每个接收者插入视角记录）
            ChatMessage saved = chatMessageService.saveMessage(
                    convType.toUpperCase(),
                    fromUserId,
                    toUserId,
                    groupId,
                    msgType.toUpperCase(),
                    req.getContent(),
                    req.getAttachmentId()
            );

            // 3. 构造并发送消息：针对不同接收方构造不同 payload，确保 toUserId 与接收者一致
            if ("PRIVATE".equalsIgnoreCase(convType)) {
                // 构造发送者视角 payload（发送给发送者自己）
                NewMessageResponse respForSender = new NewMessageResponse();
                respForSender.setId(saved.getId());
                respForSender.setConversationType(saved.getConversationType());
                respForSender.setFromUserId(saved.getFromUserId());
                respForSender.setToUserId(saved.getFromUserId()); // 发送者视角：to = 自己
                respForSender.setGroupId(saved.getGroupId());
                respForSender.setMessageType(saved.getMessageType());
                respForSender.setContent(saved.getContent());
                respForSender.setCreatedAt(saved.getCreatedAt());
                if ("IMAGE".equalsIgnoreCase(saved.getMessageType()) && saved.getAttachmentId() != null) {
                    respForSender.setImageUrl(attachmentService.generatePresignedGetUrl(saved.getAttachmentId(), 3600));
                }

                WebSocketEnvelope<NewMessageResponse> envSender = new WebSocketEnvelope<>();
                envSender.setType("NEW_MESSAGE");
                envSender.setPayload(respForSender);
                String jsonSender = objectMapper.writeValueAsString(envSender);
                TextMessage outMsgSender = new TextMessage(jsonSender);

                // 构造接收者视角 payload（确保 toUserId = 请求中的 targetUserId）
                NewMessageResponse respForReceiver = new NewMessageResponse();
                // 这里 id 使用 logicMessageId if present，否则使用 saved.id （注意：不一定等于接收者那条记录 id）
                respForReceiver.setId(saved.getLogicMessageId() != null ? saved.getLogicMessageId() : saved.getId());
                respForReceiver.setConversationType(saved.getConversationType());
                respForReceiver.setFromUserId(saved.getFromUserId());
                respForReceiver.setToUserId(toUserId); // 关键：接收者视角 to = targetUserId
                respForReceiver.setGroupId(saved.getGroupId());
                respForReceiver.setMessageType(saved.getMessageType());
                respForReceiver.setContent(saved.getContent());
                respForReceiver.setCreatedAt(saved.getCreatedAt());
                if ("IMAGE".equalsIgnoreCase(saved.getMessageType()) && saved.getAttachmentId() != null) {
                    respForReceiver.setImageUrl(attachmentService.generatePresignedGetUrl(saved.getAttachmentId(), 3600));
                }

                WebSocketEnvelope<NewMessageResponse> envReceiver = new WebSocketEnvelope<>();
                envReceiver.setType("NEW_MESSAGE");
                envReceiver.setPayload(respForReceiver);
                String jsonReceiver = objectMapper.writeValueAsString(envReceiver);
                TextMessage outMsgReceiver = new TextMessage(jsonReceiver);

                // 4. 发送：发送者自己先收到自己的 payload
                sendToUser(fromUserId, outMsgSender);
                // 再发送给接收者（如果接收者不是自己）
                if (toUserId != null && !toUserId.equals(fromUserId)) {
                    sendToUser(toUserId, outMsgReceiver);
                }
            } else if ("GROUP".equalsIgnoreCase(convType)) {
                // 为群聊每个成员构造独立 payload 并发送（确保 toUserId 为当前成员）
                NewMessageResponse baseResp = new NewMessageResponse();
                baseResp.setId(saved.getId());
                baseResp.setConversationType(saved.getConversationType());
                baseResp.setFromUserId(saved.getFromUserId());
                baseResp.setGroupId(saved.getGroupId());
                baseResp.setMessageType(saved.getMessageType());
                baseResp.setContent(saved.getContent());
                baseResp.setCreatedAt(saved.getCreatedAt());
                if ("IMAGE".equalsIgnoreCase(saved.getMessageType()) && saved.getAttachmentId() != null) {
                    baseResp.setImageUrl(attachmentService.generatePresignedGetUrl(saved.getAttachmentId(), 3600));
                }

                // 发送到群内每个成员（sendToGroup 会为每个成员构造其视角 payload）
                sendToGroup(saved.getGroupId(), baseResp);
            }

        } catch (Exception e) {
            log.error("WS handleSendMessage error, fromUserId={}", fromUserId, e);
        }
    }

    private void sendToUser(Long userId, TextMessage message) {
        java.util.Set<WebSocketSession> set = onlineUsers.get(userId);
        if (set == null || set.isEmpty()) return;
        for (WebSocketSession s : set) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(message);
                } catch (IOException e) {
                    log.warn("WS send to user {} failed: {}", userId, e.getMessage());
                }
            }
        }
    }

    /**
     * 向群内所有成员发送消息（仅群成员会收到）。
     * 为每个接收者单独构造 payload（toUserId = uid）。
     */
    private void sendToGroup(Long groupId, NewMessageResponse baseResp) {
        try {
            var memberIds = chatGroupMemberMapper.listUserIdsByGroupId(groupId);
            if (memberIds == null || memberIds.isEmpty()) {
                log.warn("WS sendToGroup: group {} has no members", groupId);
                return;
            }

            for (Long uid : memberIds) {
                if (uid == null) continue;
                NewMessageResponse respForUser = new NewMessageResponse();
                respForUser.setId(baseResp.getId());
                respForUser.setConversationType(baseResp.getConversationType());
                respForUser.setFromUserId(baseResp.getFromUserId());
                respForUser.setToUserId(uid); // 每个接收者的视角
                respForUser.setGroupId(baseResp.getGroupId());
                respForUser.setMessageType(baseResp.getMessageType());
                respForUser.setContent(baseResp.getContent());
                respForUser.setCreatedAt(baseResp.getCreatedAt());
                respForUser.setImageUrl(baseResp.getImageUrl());

                WebSocketEnvelope<NewMessageResponse> out = new WebSocketEnvelope<>();
                out.setType("NEW_MESSAGE");
                out.setPayload(respForUser);

                String json = objectMapper.writeValueAsString(out);
                TextMessage outMsg = new TextMessage(json);

                sendToUser(uid, outMsg);
            }
        } catch (Exception e) {
            log.error("WS sendToGroup failed, groupId={}", groupId, e);
        }
    }

    private void broadcast(TextMessage message) {
        for (Map.Entry<Long, java.util.Set<WebSocketSession>> entry : onlineUsers.entrySet()) {
            for (WebSocketSession s : entry.getValue()) {
                if (s.isOpen()) {
                    try {
                        s.sendMessage(message);
                    } catch (IOException e) {
                        log.warn("WS broadcast to user {} failed: {}", entry.getKey(), e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
            java.util.Set<WebSocketSession> set = onlineUsers.get(userId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    onlineUsers.remove(userId);
                }
            }
            log.info("WS disconnected: userId={} sessionId={} status={}", userId, session.getId(), status);
        }
    }

    private Long getUserId(WebSocketSession session) {
        Object uid = session.getAttributes().get("userId");
        if (uid instanceof Long l) return l;
        if (uid instanceof Integer i) return i.longValue();
        if (uid instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private void closeSession(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {}
    }
}