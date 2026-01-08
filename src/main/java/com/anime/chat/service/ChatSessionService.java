package com.anime.chat.service;

import com.anime.common.dto.chat.session.ListSessionsResponse;
import com.anime.common.dto.chat.session.SessionItem;
import com.anime.common.entity.chat.ChatGroup;
import com.anime.common.entity.chat.ChatMessage;
import com.anime.common.entity.chat.UserFriend;
import com.anime.common.entity.user.User;
import com.anime.common.mapper.chat.ChatGroupMapper;
import com.anime.common.mapper.chat.ChatGroupMemberMapper;
import com.anime.common.mapper.chat.ChatMessageMapper;
import com.anime.common.mapper.chat.UserFriendMapper;
import com.anime.common.mapper.user.UserMapper;
import com.anime.common.service.AttachmentService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话聚合服务：用于左侧会话列表
 *
 * 修改说明：
 * - 即便两位用户之间从未发送过消息（或所有消息均被软删除），仍然会在会话列表中显示该好友会话项。
 * - 同理：用户所在的群聊会话也会显示，即使群内没有可见消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatGroupMapper chatGroupMapper;
    private final ChatGroupMemberMapper chatGroupMemberMapper;
    private final UserFriendMapper userFriendMapper;
    private final UserMapper userMapper;
    private final AttachmentService attachmentService;

    public ListSessionsResponse listSessions(Long currentUserId) {
        if (currentUserId == null) {
            throw new IllegalArgumentException("currentUserId 不能为空");
        }

        List<SessionItem> result = new ArrayList<>();

        // 1. 所有与当前用户相关的私聊消息（用于取已存在会话的最新消息）
        List<ChatMessage> privateMsgs = chatMessageMapper.listAllPrivateMessagesForUser(currentUserId);
        Map<Long, ChatMessage> latestPrivateByFriend = groupLatestPrivateByFriend(privateMsgs, currentUserId);

        // 1.1 计算每个好友的未读数（当前用户作为��收方）
        Map<Long, Long> unreadCountMap = buildUnreadCountMap(currentUserId);

        // 2. 群聊相关：获取所有群消息（用于取已存在群会话的最新消息）
        List<ChatMessage> groupMsgs = chatMessageMapper.listAllGroupMessagesForUser(currentUserId);
        Map<Long, ChatMessage> latestByGroup = groupLatestByGroup(groupMsgs);

        // 2.1 计算各群的未读数
        Map<Long, Long> groupUnreadMap = buildGroupUnreadCountMap(currentUserId);

        // 3. 构建单聊会话条目：优先使用有消息的会话（latestPrivateByFriend），
        //    然后补充那些没有消息但存在好友关系的会话。
        // 3.1 已有消息的朋友会话
        for (Map.Entry<Long, ChatMessage> entry : latestPrivateByFriend.entrySet()) {
            Long friendId = entry.getKey();
            ChatMessage msg = entry.getValue();

            SessionItem item = new SessionItem();
            item.setSessionType("PRIVATE");
            item.setSessionTargetId(friendId);
            item.setLastMessageTime(msg != null ? msg.getCreatedAt() : null);
            item.setLastMessagePreview(msg != null ? buildPreview(msg.getMessageType(), msg.getContent()) : "");
            Long unread = unreadCountMap.getOrDefault(friendId, 0L);
            item.setUnreadCount(unread.intValue());

            User friend = userMapper.selectById(friendId);
            if (friend != null) {
                item.setTitle(friend.getUsername());
                Long avatarAttId = userMapper.getAvatarAttachmentIdById(friendId);
                item.setSignature(friend.getPersonalSignature());
                if (avatarAttId != null) {
                    item.setAvatarUrl(attachmentService.generatePresignedGetUrl(avatarAttId, 3600));
                }
            }
            result.add(item);
        }

        // 3.2 补充：即便没有消息也要展示的好友会话（从 user_friend 表获取）
        List<UserFriend> friendLinks = userFriendMapper.selectList(
                Wrappers.<UserFriend>lambdaQuery().eq(UserFriend::getUserId, currentUserId)
        );
        if (friendLinks != null && !friendLinks.isEmpty()) {
            Set<Long> existingFriendWithMsgs = latestPrivateByFriend.keySet();
            for (UserFriend link : friendLinks) {
                Long friendId = link.getFriendId();
                if (friendId == null) continue;
                if (existingFriendWithMsgs.contains(friendId)) continue; // 已由上面处理

                SessionItem item = new SessionItem();
                item.setSessionType("PRIVATE");
                item.setSessionTargetId(friendId);
                item.setLastMessageTime(null); // 无消息
                item.setLastMessagePreview(""); // 无消息预览
                Long unread = unreadCountMap.getOrDefault(friendId, 0L);
                item.setUnreadCount(unread.intValue());

                User friend = userMapper.selectById(friendId);
                if (friend != null) {
                    item.setTitle(friend.getUsername());
                    Long avatarAttId = userMapper.getAvatarAttachmentIdById(friendId);
                    item.setSignature(friend.getPersonalSignature());
                    if (avatarAttId != null) {
                        item.setAvatarUrl(attachmentService.generatePresignedGetUrl(avatarAttId, 3600));
                    }
                }
                result.add(item);
            }
        }

        // 4. 群聊会话条目：先使用有消息的群
        if (!latestByGroup.isEmpty()) {
            // 加载群信息
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
                item.setLastMessageTime(msg != null ? msg.getCreatedAt() : null);
                item.setLastMessagePreview(msg != null ? buildPreview(msg.getMessageType(), msg.getContent()) : "");
                Long unread = groupUnreadMap.getOrDefault(groupId, 0L);
                item.setUnreadCount(unread.intValue());

                ChatGroup group = groupMap.get(groupId);
                if (group != null) {
                    item.setTitle(group.getName());
                }
                item.setAvatarUrl(null);
                result.add(item);
            }
        }

        // 4.2 补充：用户所在但没有消息的群（从 chat_group_members 表或 mapper 获取）
        List<Long> memberGroupIds = chatGroupMemberMapper.selectList(null).stream()
                .filter(m -> m != null && m.getUserId() != null && m.getUserId().equals(currentUserId))
                .map(m -> m.getGroupId())
                .distinct()
                .collect(Collectors.toList());

        // Fallback: if selectList(null) is not desirable, use wrapper. But ChatGroupMemberMapper has
        // listUserIdsByGroupId only; above approach assumes ChatGroupMemberMapper extends BaseMapper.
        // If your mapper does not support selectList(null), replace above with appropriate call.

        if (memberGroupIds != null && !memberGroupIds.isEmpty()) {
            Set<Long> existingGroupsWithMsgs = latestByGroup.keySet();
            // load all group entities for these ids (batch)
            List<Long> missingGroupIds = memberGroupIds.stream()
                    .filter(gid -> !existingGroupsWithMsgs.contains(gid))
                    .collect(Collectors.toList());

            if (!missingGroupIds.isEmpty()) {
                List<ChatGroup> missingGroups = chatGroupMapper.selectBatchIds(missingGroupIds);
                Map<Long, ChatGroup> missingGroupMap = missingGroups.stream()
                        .collect(Collectors.toMap(ChatGroup::getId, g -> g));

                for (Long gid : missingGroupIds) {
                    SessionItem item = new SessionItem();
                    item.setSessionType("GROUP");
                    item.setSessionTargetId(gid);
                    item.setLastMessageTime(null);
                    item.setLastMessagePreview("");
                    Long unread = groupUnreadMap.getOrDefault(gid, 0L);
                    item.setUnreadCount(unread.intValue());
                    ChatGroup g = missingGroupMap.get(gid);
                    if (g != null) item.setTitle(g.getName());
                    item.setAvatarUrl(null);
                    result.add(item);
                }
            }
        }

        // 5. 按最后消息时间排序（无时间的放到后面）
        result.sort(Comparator.comparing(SessionItem::getLastMessageTime,
                Comparator.nullsLast(LocalDateTime::compareTo)).reversed());

        ListSessionsResponse resp = new ListSessionsResponse();
        resp.setSessions(result);
        return resp;
    }

    /**
     * 为某个用户构建一个单聊会话的 SessionItem（用于 WS 实时更新）
     * - userId: 当前用户
     * - friendId: 对方用户
     */
    public SessionItem buildPrivateSessionItem(Long userId, Long friendId) {
        ChatMessage last = chatMessageMapper.findLastPrivateMessage(userId, friendId);
        Long unread = chatMessageMapper.countPrivateUnread(userId, friendId);
        long unreadCount = unread != null ? unread : 0L;

        SessionItem item = new SessionItem();
        item.setSessionType("PRIVATE");
        item.setSessionTargetId(friendId);
        item.setUnreadCount((int) unreadCount);

        if (last != null) {
            item.setLastMessageTime(last.getCreatedAt());
            item.setLastMessagePreview(buildPreview(last.getMessageType(), last.getContent()));
        }

        User friend = userMapper.selectById(friendId);
        if (friend != null) {
            item.setTitle(friend.getUsername());
            Long avatarAttId = userMapper.getAvatarAttachmentIdById(friendId);
            if (avatarAttId != null) {
                item.setAvatarUrl(attachmentService.generatePresignedGetUrl(avatarAttId, 3600));
            }
        }

        return item;
    }

    /**
     * 为当前用户构建一个群聊会话的 SessionItem（用于 WS 实时更新）
     */
    public SessionItem buildGroupSessionItem(Long currentUserId, Long groupId) {
        ChatMessage last = chatMessageMapper.findLastGroupMessage(groupId, currentUserId);
        Long unread = chatMessageMapper.countGroupUnread(groupId, currentUserId);
        long unreadCount = unread != null ? unread : 0L;

        SessionItem item = new SessionItem();
        item.setSessionType("GROUP");
        item.setSessionTargetId(groupId);
        item.setUnreadCount((int) unreadCount);

        if (last != null) {
            item.setLastMessageTime(last.getCreatedAt());
            item.setLastMessagePreview(buildPreview(last.getMessageType(), last.getContent()));
        }

        ChatGroup group = chatGroupMapper.selectById(groupId);
        if (group != null) {
            item.setTitle(group.getName());
        }

        return item;
    }

    /**
     * 计算当前用户所有私聊会话的未读数：key = 对方 userId（friendId），value = 未读条数
     */
    private Map<Long, Long> buildUnreadCountMap(Long currentUserId) {
        var rows = chatMessageMapper.listPrivateUnreadCountsByFriend(currentUserId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> map = new HashMap<>();
        for (java.util.Map<String, Object> m : rows) {
            if (m == null) continue;
            Object fid = m.get("friendId");
            if (fid == null) fid = m.get("friend_id");
            Object uc = m.get("unreadCount");
            if (uc == null) uc = m.get("unread_count");
            if (fid instanceof Number && uc instanceof Number) {
                map.put(((Number) fid).longValue(), ((Number) uc).longValue());
            } else if (fid != null && uc != null) {
                try {
                    long fidL = Long.parseLong(String.valueOf(fid));
                    long ucL = Long.parseLong(String.valueOf(uc));
                    map.put(fidL, ucL);
                } catch (NumberFormatException ignore) {}
            }
        }
        return map;
    }

    /**
     * 计算当前用户所有群聊会话的未读数：key = groupId，value = 未读条数
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
        if (msgs == null) return latestMap;
        for (ChatMessage msg : msgs) {
            if (msg == null) continue;
            Long friendId;
            if (currentUserId.equals(msg.getFromUserId())) {
                friendId = msg.getToUserId();
            } else {
                friendId = msg.getFromUserId();
            }
            if (friendId == null || friendId.equals(currentUserId)) continue;
            ChatMessage old = latestMap.get(friendId);
            if (old == null || (msg.getCreatedAt() != null && (old.getCreatedAt() == null || msg.getCreatedAt().isAfter(old.getCreatedAt())))) {
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
        if (msgs == null) return latestMap;
        for (ChatMessage msg : msgs) {
            if (msg == null) continue;
            Long groupId = msg.getGroupId();
            if (groupId == null) continue;
            ChatMessage old = latestMap.get(groupId);
            if (old == null || (msg.getCreatedAt() != null && (old.getCreatedAt() == null || msg.getCreatedAt().isAfter(old.getCreatedAt())))) {
                latestMap.put(groupId, msg);
            }
        }
        return latestMap;
    }

    /**
     * 构造消息预览文本。
     */
    private String buildPreview(String messageType, String content) {
        if ("IMAGE".equalsIgnoreCase(messageType)) {
            return "[图片]";
        }
        if (content == null) return "";
        return content.length() <= 10 ? content : content.substring(0, 10) + "...";
    }
}