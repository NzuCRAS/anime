package com.anime.chat.socket;

import com.anime.chat.service.CallService;
import com.anime.chat.service.ChatMessageService;
import com.anime.chat.service.WhiteboardService;
import com.anime.common.dto.chat.call.CallAnswerRequest;
import com.anime.common.dto.chat.call.CallControlDto;
import com.anime.common.dto.chat.call.CallInviteRequest;
import com.anime.common.dto.chat.call.IceCandidateDto;
import com.anime.common.dto.chat.socket.NewMessageResponse;
import com.anime.common.dto.chat.socket.SendMessageRequest;
import com.anime.common.dto.chat.socket.WebSocketEnvelope;
import com.anime.common.dto.chat.whiteboard.WhiteboardClearRequest;
import com.anime.common.dto.chat.whiteboard.WhiteboardJoinRequest;
import com.anime.common.dto.chat.whiteboard.WhiteboardStrokePart;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 处理��：增加 TYPING_START/TYPING_STOP 事件转发（微信式“正在输入”）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatMessageService chatMessageService;
    private final AttachmentService attachmentService;
    private final ChatGroupMemberMapper chatGroupMemberMapper;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionManager sessionManager;
    private final CallService callService;
    private final WhiteboardService whiteboardService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId == null) {
            closeSession(session, CloseStatus.BAD_DATA);
            return;
        }
        sessionManager.register(userId, session);
        log.info("WS connected: userId={} sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Long userId = getUserId(session);
        if (userId == null) {
            closeSession(session, CloseStatus.BAD_DATA);
            return;
        }

        String payload = message.getPayload();
        WebSocketEnvelope<?> envelope;
        try {
            envelope = objectMapper.readValue(payload, WebSocketEnvelope.class);
        } catch (Exception e) {
            log.warn("WS parse envelope failed: {}", e.getMessage());
            return;
        }

        String type = envelope.getType();
        try {
            if ("SEND_MESSAGE".equalsIgnoreCase(type)) {
                WebSocketEnvelope<SendMessageRequest> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, SendMessageRequest.class));
                handleSendMessage(userId, env.getPayload());
                return;
            }

            // ---- Typing indicator ----
            if ("TYPING_START".equalsIgnoreCase(type) || "TYPING_STOP".equalsIgnoreCase(type)) {
                Map<?,?> p = (Map<?,?>) envelope.getPayload();
                Object tu = p == null ? null : p.get("targetUserId");
                Long targetUserId = null;
                if (tu instanceof Number n) targetUserId = n.longValue();
                else if (tu instanceof String s) try { targetUserId = Long.parseLong(s); } catch (Exception ignore) {}
                if (targetUserId == null) {
                    sessionManager.sendToUser(userId, "PEER_TYPING", Map.of("isTyping", false));
                    return;
                }
                boolean isTyping = "TYPING_START".equalsIgnoreCase(type);
                var forward = Map.of(
                        "fromUserId", userId,
                        "toUserId", targetUserId,
                        "isTyping", isTyping,
                        "ts", System.currentTimeMillis()
                );
                sessionManager.sendToUser(targetUserId, "PEER_TYPING", forward);
                return;
            }

            // --- WebRTC signaling ---
            if ("CALL_INVITE".equalsIgnoreCase(type)) {
                WebSocketEnvelope<CallInviteRequest> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, CallInviteRequest.class));
                CallInviteRequest req = env.getPayload();
                String callId = callService.createAndForwardInvite(userId, req);
                if (callId == null) {
                    sessionManager.sendToUser(userId, "CALL_FAILED", Map.of("reason", "target_unavailable_or_not_friend"));
                } else {
                    sessionManager.sendToUser(userId, "CALL_OUTGOING", Map.of("callId", callId, "targetUserId", req.getTargetUserId()));
                }
                return;
            }

            if ("CALL_ANSWER".equalsIgnoreCase(type)) {
                WebSocketEnvelope<CallAnswerRequest> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, CallAnswerRequest.class));
                CallAnswerRequest req = env.getPayload();
                boolean ok = callService.handleAnswer(userId, req);
                if (!ok) {
                    sessionManager.sendToUser(userId, "CALL_FAILED", Map.of("reason", "invalid_call_or_not_allowed"));
                }
                return;
            }

            if ("CALL_ICE".equalsIgnoreCase(type)) {
                WebSocketEnvelope<IceCandidateDto> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, IceCandidateDto.class));
                IceCandidateDto c = env.getPayload();
                boolean ok = callService.handleIce(userId, c);
                if (!ok) {
                    log.debug("Failed to forward ICE candidate for userId={} callId={}", userId, c == null ? null : c.getCallId());
                }
                return;
            }

            if ("CALL_HANGUP".equalsIgnoreCase(type)) {
                WebSocketEnvelope<CallControlDto> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, CallControlDto.class));
                CallControlDto ctrl = env.getPayload();
                callService.handleHangup(userId, ctrl, "CALL_HANGUP");
                return;
            }

            if ("CALL_REJECT".equalsIgnoreCase(type) || "CALL_ACCEPT".equalsIgnoreCase(type)) {
                WebSocketEnvelope<CallControlDto> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, CallControlDto.class));
                CallControlDto ctrl = env.getPayload();
                String ev = type.equalsIgnoreCase("CALL_REJECT") ? "CALL_REJECT" : "CALL_ACCEPT";
                callService.handleHangup(userId, ctrl, ev);
                return;
            }

            // --------------- whiteboard (new) ----------------
            if ("WHITEBOARD_OPEN".equalsIgnoreCase(type) || "WHITEBOARD_CREATE".equalsIgnoreCase(type)) {
                WebSocketEnvelope<WhiteboardJoinRequest> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, WhiteboardJoinRequest.class));
                WhiteboardJoinRequest req = env.getPayload();
                Long targetUserId = req.getTargetUserId();
                if (targetUserId == null) {
                    sessionManager.sendToUser(userId, "WHITEBOARD_ERROR", Map.of("reason", "target_required"));
                } else {
                    whiteboardService.openWhiteboardIfNeeded(userId, targetUserId);
                    sessionManager.sendToUser(userId, "WHITEBOARD_OPENED", Map.of("targetUserId", targetUserId));
                }
                return;
            }

            if ("WHITEBOARD_JOIN".equalsIgnoreCase(type)) {
                WebSocketEnvelope<WhiteboardJoinRequest> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, WhiteboardJoinRequest.class));
                WhiteboardJoinRequest req = env.getPayload();
                Long targetUserId = req.getTargetUserId();
                if (targetUserId == null) {
                    sessionManager.sendToUser(userId, "WHITEBOARD_ERROR", Map.of("reason", "target_required"));
                    return;
                }
                whiteboardService.openWhiteboardIfNeeded(userId, targetUserId);
                List<Map<String, Object>> events = whiteboardService.joinAndLoadWindow(userId, targetUserId);
                sessionManager.sendToUser(userId, "WHITEBOARD_INIT", Map.of("events", events));
                return;
            }

            if ("WHITEBOARD_STROKE_PART".equalsIgnoreCase(type)) {
                WebSocketEnvelope<WhiteboardStrokePart> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, WhiteboardStrokePart.class));
                WhiteboardStrokePart p = env.getPayload();
                Long targetUserId = p.getTargetUserId();
                if (targetUserId == null) {
                    sessionManager.sendToUser(userId, "WHITEBOARD_ERROR", Map.of("reason", "target_required"));
                    return;
                }

                Map<String, Object> ev = new HashMap<>();
                ev.put("type", "WHITEBOARD_STROKE_PART");
                ev.put("strokeId", p.getStrokeId());
                ev.put("tool", p.getTool());
                ev.put("color", p.getColor());
                ev.put("width", p.getWidth());
                ev.put("points", p.getPoints());
                ev.put("isEnd", p.getIsEnd());
                ev.put("ts", p.getTs() == null ? System.currentTimeMillis() : p.getTs());

                whiteboardService.appendStrokeAndForward(userId, targetUserId, ev);
                return;
            }

            if ("WHITEBOARD_CLEAR".equalsIgnoreCase(type)) {
                WebSocketEnvelope<WhiteboardClearRequest> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, WhiteboardClearRequest.class));
                WhiteboardClearRequest r = env.getPayload();
                Long targetUserId = r.getTargetUserId();
                if (targetUserId == null) {
                    sessionManager.sendToUser(userId, "WHITEBOARD_ERROR", Map.of("reason", "target_required"));
                    return;
                }
                whiteboardService.clearAndBroadcast(userId, targetUserId, r.getTs());
                return;
            }

            if ("WHITEBOARD_LEAVE".equalsIgnoreCase(type)) {
                WebSocketEnvelope<WhiteboardJoinRequest> env =
                        objectMapper.readValue(payload,
                                objectMapper.getTypeFactory().constructParametricType(WebSocketEnvelope.class, WhiteboardJoinRequest.class));
                WhiteboardJoinRequest req = env.getPayload();
                Long targetUserId = req.getTargetUserId();
                if (targetUserId != null) {
                    whiteboardService.leave(userId, targetUserId);
                }
                return;
            }

            log.debug("WS unknown type: {}", type);

        } catch (Exception e) {
            log.error("WS handleTextMessage error for type={} userId={}", type, userId, e);
        }
    }

    private void handleSendMessage(Long fromUserId, SendMessageRequest req) {
        try {
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

            ChatMessage saved = chatMessageService.saveMessage(
                    convType.toUpperCase(),
                    fromUserId,
                    toUserId,
                    groupId,
                    msgType.toUpperCase(),
                    req.getContent(),
                    req.getAttachmentId(),
                    req.getClientMessageId()
            );

            try {
                Map<String, Object> ackPayload = new HashMap<>();
                if (req.getClientMessageId() != null) {
                    ackPayload.put("clientMessageId", req.getClientMessageId());
                }
                Long serverMessageId = saved.getLogicMessageId() != null ? saved.getLogicMessageId() : saved.getId();
                ackPayload.put("serverMessageId", serverMessageId);
                ackPayload.put("status", "received");
                ackPayload.put("ts", System.currentTimeMillis());

                WebSocketEnvelope<Map<String, Object>> ackEnv = new WebSocketEnvelope<>();
                ackEnv.setType("ACK");
                ackEnv.setPayload(ackPayload);
                String ackJson = objectMapper.writeValueAsString(ackEnv);
                sessionManager.sendToUser(fromUserId, new TextMessage(ackJson));
            } catch (Exception e) {
                log.warn("Failed to send ACK to user {}: {}", fromUserId, e.getMessage());
            }

            if ("PRIVATE".equalsIgnoreCase(convType)) {
                NewMessageResponse respForReceiver = new NewMessageResponse();
                respForReceiver.setId(saved.getLogicMessageId() != null ? saved.getLogicMessageId() : saved.getId());
                respForReceiver.setConversationType(saved.getConversationType());
                respForReceiver.setFromUserId(saved.getFromUserId());
                respForReceiver.setToUserId(toUserId);
                respForReceiver.setGroupId(saved.getGroupId());
                respForReceiver.setMessageType(saved.getMessageType());
                respForReceiver.setContent(saved.getContent());
                respForReceiver.setCreatedAt(saved.getCreatedAt());
                if (saved.getAttachmentId() != null) {
                    try {
                        respForReceiver.setFileUrl(attachmentService.generatePresignedGetUrl(saved.getAttachmentId(), 3600));
                    } catch (Exception ex) {
                        log.error("failed to generate presigned url for attachment {}: {}", saved.getAttachmentId(), ex.getMessage());
                    }
                }

                WebSocketEnvelope<NewMessageResponse> envReceiver = new WebSocketEnvelope<>();
                envReceiver.setType("NEW_MESSAGE");
                envReceiver.setPayload(respForReceiver);
                String jsonReceiver = objectMapper.writeValueAsString(envReceiver);
                TextMessage outMsgReceiver = new TextMessage(jsonReceiver);

                sessionManager.sendToUser(fromUserId, outMsgReceiver);
                if (!toUserId.equals(fromUserId)) {
                    sessionManager.sendToUser(toUserId, outMsgReceiver);
                }
            } else if ("GROUP".equalsIgnoreCase(convType)) {
                NewMessageResponse baseResp = new NewMessageResponse();
                baseResp.setId(saved.getId());
                baseResp.setConversationType(saved.getConversationType());
                baseResp.setFromUserId(saved.getFromUserId());
                baseResp.setGroupId(saved.getGroupId());
                baseResp.setMessageType(saved.getMessageType());
                baseResp.setContent(saved.getContent());
                baseResp.setCreatedAt(saved.getCreatedAt());
                if (saved.getAttachmentId() != null) {
                    try {
                        baseResp.setFileUrl(attachmentService.generatePresignedGetUrl(saved.getAttachmentId(), 3600));
                    } catch (Exception ex) {
                        log.warn("failed to generate presigned url for attachment {}: {}", saved.getAttachmentId(), ex.getMessage());
                    }
                }

                sendToGroup(saved.getGroupId(), baseResp);
            }

        } catch (Exception e) {
            log.error("WS handleSendMessage error, fromUserId={}", fromUserId, e);
        }
    }

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
                respForUser.setToUserId(uid);
                respForUser.setGroupId(baseResp.getGroupId());
                respForUser.setMessageType(baseResp.getMessageType());
                respForUser.setContent(baseResp.getContent());
                respForUser.setCreatedAt(baseResp.getCreatedAt());
                respForUser.setFileUrl(baseResp.getFileUrl());

                WebSocketEnvelope<NewMessageResponse> out = new WebSocketEnvelope<>();
                out.setType("NEW_MESSAGE");
                out.setPayload(respForUser);

                String json = objectMapper.writeValueAsString(out);
                TextMessage outMsg = new TextMessage(json);

                sessionManager.sendToUser(uid, outMsg);
            }
        } catch (Exception e) {
            log.error("WS sendToGroup failed, groupId={}", groupId, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
            sessionManager.unregister(userId, session);
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