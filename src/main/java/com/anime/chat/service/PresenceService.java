package com.anime.chat.service;

import com.anime.chat.socket.WsEventPublisher;
import com.anime.common.enums.SocketType;
import com.anime.common.entity.chat.UserFriend;
import com.anime.common.mapper.chat.UserFriendMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PresenceService - 维护在线用户集合，并向好友推送在线/离线事件。
 *
 * 设计：
 * - 当用户首次上线（由 WebSocketSessionManager 检测到）调用 userOnline(userId)
 * - 当用户最后一条 session 被注销时调用 userOffline(userId)
 * - 只在状态发生变化（从 offline->online 或 online->offline）时进行广播
 */
@Slf4j
@Component
public class PresenceService {

    private final UserFriendMapper userFriendMapper;

    // WsEventPublisher 使用懒注入，避免与 WebSocketSessionManager/它的依赖形成循环
    @Autowired
    @Lazy
    private WsEventPublisher wsEventPublisher;

    // 仅存在线用户 id（用于避免重复广播）
    private final Set<Long> onlineUsers = ConcurrentHashMap.newKeySet();

    public PresenceService(UserFriendMapper userFriendMapper) {
        this.userFriendMapper = userFriendMapper;
    }

    /**
     * 标记用户上线（第一次上线时广播）
     */
    public void userOnline(Long userId) {
        if (userId == null) return;
        boolean first = onlineUsers.add(userId);
        if (!first) {
            log.debug("PresenceService.userOnline: user {} already online, skip broadcast", userId);
            return;
        }

        try {
            // 查询该用户的好友（user_friend 表中 user_id = userId）
            var links = userFriendMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<UserFriend>lambdaQuery()
                            .eq(UserFriend::getUserId, userId)
            );
            if (links == null || links.isEmpty()) {
                log.debug("PresenceService.userOnline: user {} has no friends to notify", userId);
                return;
            }
            for (UserFriend l : links) {
                if (l == null || l.getFriendId() == null) continue;
                // payload 可随需要扩展（这里使用最简单的 { userId }）
                var payload = java.util.Map.of("userId", userId);
                wsEventPublisher.sendToUser(l.getFriendId(), SocketType.USER_ONLINE.toString(), payload);
                log.info("广播 userId={} 在线给 friendId={}", userId, l.getFriendId());
            }
            log.info("PresenceService: broadcast USER_ONLINE for user={} to {} friends", userId, links.size());
        } catch (Exception e) {
            log.warn("PresenceService.userOnline: failed to notify friends for user={}, err={}", userId, e.getMessage(), e);
        }
    }

    /**
     * 标记用户下线（最后一条 session 注销时广播）
     */
    public void userOffline(Long userId) {
        if (userId == null) return;
        boolean removed = onlineUsers.remove(userId);
        if (!removed) {
            log.debug("PresenceService.userOffline: user {} was not marked online, skip broadcast", userId);
            return;
        }

        try {
            var links = userFriendMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<UserFriend>lambdaQuery()
                            .eq(UserFriend::getUserId, userId)
            );
            if (links == null || links.isEmpty()) {
                log.debug("PresenceService.userOffline: user {} has no friends to notify", userId);
                return;
            }
            for (UserFriend l : links) {
                if (l == null || l.getFriendId() == null) continue;
                var payload = Map.of("userId", userId);
                wsEventPublisher.sendToUser(l.getFriendId(), SocketType.USER_OFFLINE.toString(), payload);
                log.info("广播 userId={} 离线给 friendId={}", userId, l.getFriendId());
            }
            log.info("PresenceService: broadcast USER_OFFLINE for user={} to {} friends", userId, links.size());
        } catch (Exception e) {
            log.warn("PresenceService.userOffline: failed to notify friends for user={}, err={}", userId, e.getMessage(), e);
        }
    }

    /**
     * 可查询某用户当前是否在线（主要用于内部或测试）
     */
    public boolean isOnline(Long userId) {
        if (userId == null) return false;
        return onlineUsers.contains(userId);
    }
}