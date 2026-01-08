package com.anime.chat.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 管理 WebSocketSession，并提供向特定 userId 发送消息的能力。
 *
 * 关键：为了避免 Tomcat 的 "TEXT_PARTIAL_WRITING" 异常，
 * 我们为每个 WebSocketSession 创建一个单线程 Executor（序列化该 session 的所有发送）。
 *
 * 另外提供了与现有 ChatWebSocketHandler 兼容的便捷方法：
 * - register(userId, session) / unregister(userId, session)
 * - sendToUser(userId, TextMessage)   （直接发送已序列化的 TextMessage）
 * - sendToUser(userId, type, payload) （按 envelope 序列化后发送）
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final ObjectMapper objectMapper;

    // userId -> sessions
    private final ConcurrentMap<Long, CopyOnWriteArraySet<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    // sessionId -> single-thread executor for serializing sends to that session
    // 每个socket分配一个线程
    private final ConcurrentMap<String, ExecutorService> sessionExecutors = new ConcurrentHashMap<>();

    // shared pool for lightweight tasks (not actual sends)
    private final ExecutorService sharedExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("ws-shared-exec-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    public WebSocketSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 兼容旧名：注册 session（通常在 WebSocket 握手/连接时调用）
     */
    public void register(Long userId, WebSocketSession session) {
        registerSession(userId, session);
    }

    /**
     * 注册 session（通常在 WebSocket 握手/连接时调用）
     */
    public void registerSession(Long userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        sessionsByUser.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        // create per-session single-thread executor
        sessionExecutors.compute(session.getId(), (sid, existing) -> {
            if (existing == null || existing.isShutdown() || existing.isTerminated()) {
                ThreadFactory tf = r -> {
                    Thread t = new Thread(r);
                    t.setName("ws-send-" + sid);
                    t.setDaemon(true);
                    return t;
                };
                return Executors.newSingleThreadExecutor(tf);
            }
            return existing;
        });
        log.debug("register session userId={} sessionId={} totalSessionsForUser={}",
                userId, session.getId(), sessionsByUser.getOrDefault(userId, new CopyOnWriteArraySet<>()).size());
    }

    /**
     * 兼容旧名：注销 session（通常在断开连接时调用）
     */
    public void unregister(Long userId, WebSocketSession session) {
        unregisterSession(userId, session);
    }

    /**
     * 注销 session（通常在断开连接时调用）
     */
    public void unregisterSession(Long userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        Set<WebSocketSession> set = sessionsByUser.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessionsByUser.remove(userId, Collections.emptySet());
            }
        }
        ExecutorService ex = sessionExecutors.remove(session.getId());
        if (ex != null) {
            try {
                ex.shutdownNow();
            } catch (Exception ignore) {}
        }
        log.debug("unregister session userId={} sessionId={} remainingForUser={}",
                userId, session.getId(), sessionsByUser.getOrDefault(userId, new CopyOnWriteArraySet<>()).size());
    }

    /**
     * 发送已构造好的 TextMessage 到某个 userId（会发送到该用户的所有活跃 session 上）。
     * 这是对 ChatWebSocketHandler 现有调用的兼容接口（它有时已经在上层把消息序列化成 TextMessage）。
     */
    public void sendToUser(Long userId, TextMessage textMessage) {
        if (userId == null || textMessage == null) return;
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("sendToUser(TextMessage): no sessions for userId={}", userId);
            return;
        }

        for (WebSocketSession s : sessions) {
            if (s == null) continue;
            ExecutorService ex = sessionExecutors.get(s.getId());
            if (ex == null) {
                // create executor if missing (race)
                ex = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setName("ws-send-" + s.getId());
                    t.setDaemon(true);
                    return t;
                });
                ExecutorService prev = sessionExecutors.putIfAbsent(s.getId(), ex);
                if (prev != null) {
                    ex.shutdownNow();
                    ex = prev;
                }
            }

            final WebSocketSession sessionRef = s;
            final TextMessage msgRef = textMessage;
            final ExecutorService executorRef = ex;
            try {
                executorRef.submit(() -> {
                    try {
                        synchronized (sessionRef) {
                            if (!sessionRef.isOpen()) {
                                log.debug("sendToUser(TextMessage): session {} closed, skipping", sessionRef.getId());
                                return;
                            }
                            sessionRef.sendMessage(msgRef);
                        }
                    } catch (Throwable sendErr) {
                        log.warn("sendToUser(TextMessage): unexpected error sending to userId={} sessionId={} err={}",
                                userId, sessionRef.getId(), sendErr.getMessage(), sendErr);
                        try { sessionRef.close(); } catch (Exception ignore) {}
                        try { unregisterSession(userId, sessionRef); } catch (Exception ignore2) {}
                    }
                });
            } catch (RejectedExecutionException rej) {
                log.warn("sendToUser(TextMessage): executor rejected for sessionId={}, userId={}", s.getId(), userId);
            }
        }
    }

    /**
     * 发送消息到某个 userId（会发送到该用户的所有活跃 session 上）。
     *
     * payloadObject 会被序列化为 JSON，消息 envelope 可由上层封装为 { type, payload }。
     */
    public void sendToUser(Long userId, String type, Object payloadObject) {
        if (userId == null) return;
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("sendToUser: no sessions for userId={}", userId);
            return;
        }

        // prepare JSON once
        final String jsonPayload;
        try {
            java.util.Map<String, Object> env = new java.util.HashMap<>();
            env.put("type", type);
            env.put("payload", payloadObject);
            jsonPayload = objectMapper.writeValueAsString(env);
        } catch (Exception e) {
            log.warn("sendToUser: failed to serialize payload for userId={}, type={}, err={}", userId, type, e.getMessage(), e);
            return;
        }

        for (WebSocketSession s : sessions) {
            if (s == null) continue;
            ExecutorService ex = sessionExecutors.get(s.getId());
            if (ex == null) {
                // create executor if missing (race)
                ex = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setName("ws-send-" + s.getId());
                    t.setDaemon(true);
                    return t;
                });
                ExecutorService prev = sessionExecutors.putIfAbsent(s.getId(), ex);
                if (prev != null) {
                    ex.shutdownNow();
                    ex = prev;
                }
            }

            final WebSocketSession sessionRef = s;
            final String payload = jsonPayload;
            final ExecutorService executorRef = ex;
            try {
                executorRef.submit(() -> {
                    // extra guard: synchronized on session to be extra-safe with underlying impl
                    try {
                        synchronized (sessionRef) {
                            if (!sessionRef.isOpen()) {
                                log.debug("sendToUser: session {} closed, skipping", sessionRef.getId());
                                return;
                            }
                            sessionRef.sendMessage(new TextMessage(payload));
                        }
                    } catch (Throwable sendErr) {
                        log.warn("sendToUser: unexpected error sending to userId={} sessionId={} err={}",
                                userId, sessionRef.getId(), sendErr.getMessage(), sendErr);
                        // 若发送失败，尝试注销该 session，避免后续不断失败
                        try {
                            sessionRef.close();
                        } catch (Exception ignore) {}
                        try {
                            // we have the userId already
                            unregisterSession(userId, sessionRef);
                        } catch (Exception ignore2) {}
                    }
                });
            } catch (RejectedExecutionException rej) {
                log.warn("sendToUser: executor rejected for sessionId={}, userId={}", s.getId(), userId);
            }
        }
    }

    /**
     * 可选：查询用户当前的活跃 session 数
     */
    public int countSessionsForUser(Long userId) {
        Set<WebSocketSession> set = sessionsByUser.get(userId);
        return set == null ? 0 : set.size();
    }

    /**
     * 在应用关闭时可以调用以释放资源（视需要）
     */
    public void shutdown() {
        try {
            sharedExecutor.shutdownNow();
        } catch (Exception ignore) {}
        for (ExecutorService ex : sessionExecutors.values()) {
            try { ex.shutdownNow(); } catch (Exception ignore) {}
        }
        sessionExecutors.clear();
    }
}