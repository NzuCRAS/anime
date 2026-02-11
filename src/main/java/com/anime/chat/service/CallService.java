package com.anime.chat.service;

import com.anime.common.dto.chat.call.CallAnswerRequest;
import com.anime.common.dto.chat.call.CallControlDto;
import com.anime.common.dto.chat.call.CallInviteRequest;
import com.anime.common.dto.chat.call.IceCandidateDto;
import com.anime.common.entity.chat.UserFriend;
import com.anime.common.mapper.chat.UserFriendMapper;
import com.anime.chat.socket.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CallService - 简单信令管理：
 * - 管理 callId -> CallSession（callerId, calleeId, state）
 * - 使用 WebSocketSessionManager 直接向目标用户推送信令 envelope（type + payload）
 *
 * 这不是媒体代理，仅做信令转发与简单状态管理。
 */
@Slf4j
@Service
public class CallService {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final UserFriendMapper userFriendMapper;

    public CallService(WebSocketSessionManager sessionManager,
                       ObjectMapper objectMapper,
                       UserFriendMapper userFriendMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.userFriendMapper = userFriendMapper;
    }

    private static class CallSession {
        final Long callerId;
        final Long calleeId;
        volatile State state;
        CallSession(Long callerId, Long calleeId, State state) {
            this.callerId = callerId; this.calleeId = calleeId; this.state = state;
        }
    }

    private enum State { INVITED, CONNECTED, ENDED }

    // callId -> session
    private final Map<String, CallSession> calls = new ConcurrentHashMap<>();

    /**
     * 发起呼叫（caller 发来 offer）：
     * - 生成 callId（如果请求中没提供）
     * - 校验好友关系（可选）
     * - 如果目标在线则把邀请转发给目标（CALL_INVITE），并在本地注册会话
     * - 如果目标不在线则直接返回 null 或抛异常
     */
    public String createAndForwardInvite(Long callerId, CallInviteRequest req) {
        Long calleeId = req.getTargetUserId();
        if (calleeId == null) throw new IllegalArgumentException("targetUserId required");

        // 可选：只允许好友互相呼叫 — 若不需要可去掉这段
        try {
            var friends = userFriendMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<UserFriend>lambdaQuery()
                            .eq(UserFriend::getUserId, callerId)
                            .eq(UserFriend::getFriendId, calleeId)
            );
            if (friends == null || friends.isEmpty()) {
                log.info("CallService: caller {} is not friend with {}", callerId, calleeId);
                return null;
            }
        } catch (Throwable t) {
            log.warn("CallService: friend check failed, allowing call by default: {}", t.getMessage());
        }

        String callId = req.getCallId();
        if (callId == null || callId.isBlank()) callId = UUID.randomUUID().toString();

        CallSession cs = new CallSession(callerId, calleeId, State.INVITED);
        calls.put(callId, cs);

        // build payload to callee using a mutable map (allows null values)
        Map<String, Object> payload = new HashMap<>();
        payload.put("callId", callId);
        payload.put("fromUserId", callerId);
        // sdp may be an object (e.g. { sdp: "...", type: "offer" })
        payload.put("sdp", req.getSdp());
        payload.put("metadata", req.getMetadata());

        // send to callee using sessionManager
        try {
            sessionManager.sendToUser(calleeId, "CALL_INVITE", payload);
            log.info("CallService: forwarded CALL_INVITE callId={} from {} -> {}", callId, callerId, calleeId);
        } catch (Exception e) {
            log.warn("CallService: failed to forward CALL_INVITE callId={} err={}", callId, e.getMessage(), e);
        }
        return callId;
    }

    /**
     * 处理 answer：找到 call 会话并把 answer 转发给另一方（caller）。
     */
    public boolean handleAnswer(Long responderId, CallAnswerRequest req) {
        String callId = req.getCallId();
        if (callId == null) return false;
        CallSession cs = calls.get(callId);
        if (cs == null) {
            log.warn("CallService.handleAnswer: unknown callId={}", callId);
            return false;
        }
        // Only allow callee to send answer to caller
        if (!responderId.equals(cs.calleeId)) {
            log.warn("CallService.handleAnswer: responder {} not callee for call {}", responderId, callId);
            return false;
        }
        cs.state = State.CONNECTED;

        Map<String, Object> payload = new HashMap<>();
        payload.put("callId", callId);
        payload.put("fromUserId", responderId);
        payload.put("sdp", req.getSdp());

        try {
            sessionManager.sendToUser(cs.callerId, "CALL_ANSWER", payload);
            log.info("CallService: forwarded CALL_ANSWER callId={} from {} -> {}", callId, responderId, cs.callerId);
        } catch (Exception e) {
            log.warn("CallService: failed to forward CALL_ANSWER callId={} err={}", callId, e.getMessage(), e);
            return false;
        }
        return true;
    }

    /**
     * 中继 ICE candidate：寻找 call，并把 candidate 转发给对等方
     */
    public boolean handleIce(Long senderId, IceCandidateDto c) {
        if (c == null || c.getCallId() == null) return false;
        CallSession cs = calls.get(c.getCallId());
        if (cs == null) {
            log.warn("CallService.handleIce: unknown callId={}", c.getCallId());
            return false;
        }
        Long peer;
        if (senderId.equals(cs.callerId)) peer = cs.calleeId;
        else if (senderId.equals(cs.calleeId)) peer = cs.callerId;
        else {
            log.warn("CallService.handleIce: sender {} not part of call {}", senderId, c.getCallId());
            return false;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("callId", c.getCallId());
        payload.put("fromUserId", senderId);
        payload.put("candidate", c.getCandidate());
        payload.put("sdpMid", c.getSdpMid());
        payload.put("sdpMLineIndex", c.getSdpMLineIndex());

        try {
            sessionManager.sendToUser(peer, "CALL_ICE", payload);
        } catch (Exception e) {
            log.warn("CallService: failed to forward CALL_ICE callId={} err={}", c.getCallId(), e.getMessage(), e);
            return false;
        }
        return true;
    }

    /**
     * 处理挂断（或拒绝）：通知另一方并标记会话结束
     */
    public boolean handleHangup(Long senderId, CallControlDto ctrl, String eventType) {
        if (ctrl == null || ctrl.getCallId() == null) return false;
        String callId = ctrl.getCallId();
        CallSession cs = calls.remove(callId);
        if (cs == null) {
            log.warn("CallService.handleHangup: unknown callId={}", callId);
            return false;
        }
        Long peer = senderId.equals(cs.callerId) ? cs.calleeId : cs.callerId;

        Map<String, Object> payload = new HashMap<>();
        payload.put("callId", callId);
        payload.put("fromUserId", senderId);
        payload.put("reason", ctrl.getReason());

        try {
            sessionManager.sendToUser(peer, eventType, payload);
            log.info("CallService: forwarded {} callId={} from {} -> {}", eventType, callId, senderId, peer);
        } catch (Exception e) {
            log.warn("CallService: failed to forward {} callId={} err={}", eventType, callId, e.getMessage(), e);
            return false;
        }
        return true;
    }

    /**
     * 查询 call 是否存在（用于前端/调试）
     */
    public boolean exists(String callId) {
        return callId != null && calls.containsKey(callId);
    }

    /**
     * 若需要，可以提供结束并通知两个端的方法
     */
    public void forceEndCall(String callId) {
        CallSession cs = calls.remove(callId);
        if (cs == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("callId", callId);
        payload.put("reason", "terminated_by_server");
        sessionManager.sendToUser(cs.callerId, "CALL_ENDED", payload);
        sessionManager.sendToUser(cs.calleeId, "CALL_ENDED", payload);
    }
}