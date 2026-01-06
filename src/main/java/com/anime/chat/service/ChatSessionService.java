package com.anime.chat.service;

import com.anime.common.dto.chat.session.ListSessionsRequest;
import com.anime.common.dto.chat.session.ListSessionsResponse;
import com.anime.common.dto.chat.session.SessionItem;
import com.anime.common.entity.chat.ChatGroup;
import com.anime.common.entity.chat.ChatMessage;
import com.anime.common.entity.user.User;
import com.anime.common.mapper.chat.ChatGroupMapper;
import com.anime.common.mapper.chat.ChatMessageMapper;
import com.anime.common.mapper.user.UserMapper;
import com.anime.common.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话聚合服务：用于左侧会话列表
 */
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatGroupMapper chatGroupMapper;
    private final UserMapper userMapper;
    private final AttachmentService attachmentService;

    public ListSessionsResponse listSessions(ListSessionsRequest request, Long currentUserId) {
        List<SessionItem> result = new ArrayList<>();

        // 1. 所有与当前用户相关的私聊消息
        List<ChatMessage> privateMsgs = chatMessageMapper.listAllPrivateMessagesForUser(currentUserId);
        Map<Long, ChatMessage> latestPrivateByFriend = groupLatestPrivateByFriend(privateMsgs, currentUserId);

        // 1.1 计算每个好友的未读数（当前用户作为接收方）
        Map<Long, Long> unreadCountMap = buildUnreadCountMap(currentUserId);

        // 2. 所有与当前用户相关的群聊消息
        List<ChatMessage> groupMsgs = chatMessageMapper.listAllGroupMessagesForUser(currentUserId);
        Map<Long, ChatMessage> latestByGroup = groupLatestByGroup(groupMsgs);

        // 2.1 计算各群的未读数
        Map<Long, Long> groupUnreadMap = buildGroupUnreadCountMap(currentUserId);

        // 3. 单聊会话条目
        for (Map.Entry<Long, ChatMessage> entry : latestPrivateByFriend.entrySet()) {
            Long friendId = entry.getKey();
            ChatMessage msg = entry.getValue();

            SessionItem item = new SessionItem();
            item.setSessionType("PRIVATE");
            item.setSessionTargetId(friendId);
            item.setLastMessageTime(msg.getCreatedAt());
            item.setLastMessagePreview(buildPreview(msg.getMessageType(), msg.getContent()));

            Long unread = unreadCountMap.getOrDefault(friendId, 0L);
            item.setUnreadCount(unread.intValue());

            User friend = userMapper.selectById(friendId);
            if (friend != null) {
                item.setTitle(friend.getUsername());
                Long avatarAttId = userMapper.getAvatarAttachmentIdById(friendId);
                if (avatarAttId != null) {
                    item.setAvatarUrl(attachmentService.generatePresignedGetUrl(avatarAttId, 3600));
                }
            }
            result.add(item);
        }

        // 4. 群聊会话条目
        if (!latestByGroup.isEmpty()) {
            // 加载所有涉及到的群信息
            List<Long> groupIds = new ArrayList<>(latestByGroup.keySet());
            List<ChatGroup> groups = chatGroupMapper.selectBatchIds(groupIds);
            Map<Long, ChatGroup> groupMap = groups.stream()
                    .collect(Collectors.toMap(ChatGroup::getId, g -> g));

            for (Map.Entry<Long, ChatMessage> entry : latestByGroup.entrySet()) {
                Long groupId = entry.getKey();
                ChatMessage msg = entry.getValue();

                SessionItem item = new SessionItem();
                item.setSessionType("GROUP");
                item.setSessionTargetId(groupId);
                item.setLastMessageTime(msg.getCreatedAt());
                item.setLastMessagePreview(buildPreview(msg.getMessageType(), msg.getContent()));

                Long unread = groupUnreadMap.getOrDefault(groupId, 0L);
                item.setUnreadCount(unread.intValue());

                ChatGroup group = groupMap.get(groupId);
                if (group != null) {
                    item.setTitle(group.getName());
                }
                // 若后续在 chat_groups 表中加 avatar_attachment_id，可在此生成群头像 URL
                item.setAvatarUrl(null);

                result.add(item);
            }
        }

        // 5. 按最后消息时间排序（新 → 旧）
        result.sort(Comparator.comparing(SessionItem::getLastMessageTime,
                Comparator.nullsLast(LocalDateTime::compareTo)).reversed());

        ListSessionsResponse resp = new ListSessionsResponse();
        resp.setSessions(result);
        return resp;
    }

    /**
     * 计算当前用户所有私聊会话的未读数：
     * key = 对方 userId（friendId），value = 未读条数
     */
    private Map<Long, Long> buildUnreadCountMap(Long currentUserId) {
        var rows = chatMessageMapper.listPrivateUnreadCountsByFriend(currentUserId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> map = new HashMap<>();
        for (ChatMessageMapper.PrivateUnreadCountRow row : rows) {
            if (row.getFriendId() != null && row.getUnreadCount() != null) {
                map.put(row.getFriendId(), row.getUnreadCount());
            }
        }
        return map;
    }

    /**
     * 计算当前用户所有群聊会话的未读数：
     * key = groupId，value = 未读条数
     */
    private Map<Long, Long> buildGroupUnreadCountMap(Long currentUserId) {
        var rows = chatMessageMapper.listGroupUnreadCountsByGroup(currentUserId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> map = new HashMap<>();
        for (ChatMessageMapper.GroupUnreadCountRow row : rows) {
            if (row.getGroupId() != null && row.getUnreadCount() != null) {
                map.put(row.getGroupId(), row.getUnreadCount());
            }
        }
        return map;
    }

    /**
     * 按“对方 userId”分组，取每个单聊会话的最新消息。
     */
    private Map<Long, ChatMessage> groupLatestPrivateByFriend(List<ChatMessage> msgs, Long currentUserId) {
        Map<Long, ChatMessage> latestMap = new HashMap<>();
        for (ChatMessage msg : msgs) {
            Long friendId;
            // 当前用户是发送者
            if (currentUserId.equals(msg.getFromUserId())) {
                friendId = msg.getToUserId();
            } else {
                // 当前用户是接收者
                friendId = msg.getFromUserId();
            }
            // 排除 friendId 为空或者等于自己（不展示“和自己聊天”的会话）
            if (friendId == null || friendId.equals(currentUserId)) continue;

            ChatMessage old = latestMap.get(friendId);
            if (old == null || msg.getCreatedAt().isAfter(old.getCreatedAt())) {
                latestMap.put(friendId, msg);
            }
        }
        return latestMap;
    }

    /**
     * 按 groupId 分组，取每个群会话的最新消息。
     */
    private Map<Long, ChatMessage> groupLatestByGroup(List<ChatMessage> msgs) {
        Map<Long, ChatMessage> latestMap = new HashMap<>();
        for (ChatMessage msg : msgs) {
            Long groupId = msg.getGroupId();
            if (groupId == null) continue;

            ChatMessage old = latestMap.get(groupId);
            if (old == null || msg.getCreatedAt().isAfter(old.getCreatedAt())) {
                latestMap.put(groupId, msg);
            }
        }
        return latestMap;
    }

    /**
     * 构造消息预览文本：
     * - 图片消息显示 "[图片]"
     * - 文本消息截取前 30 个字符
     */
    private String buildPreview(String messageType, String content) {
        if ("IMAGE".equalsIgnoreCase(messageType)) {
            return "[图片]";
        }
        if (content == null) return "";
        return content.length() <= 30 ? content : content.substring(0, 30) + "...";
    }
}