package com.anime.chat.service;

import com.anime.chat.socket.WsEventPublisher;
import com.anime.common.dto.chat.message.*;
import com.anime.common.dto.chat.session.SessionItem;
import com.anime.common.entity.chat.ChatMessage;
import com.anime.common.entity.user.User;
import com.anime.common.enums.SocketType;
import com.anime.common.mapper.chat.ChatGroupMemberMapper;
import com.anime.common.mapper.chat.ChatMessageMapper;
import com.anime.common.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.Socket;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天消息业务逻辑（已加入 clientMessageId 幂等及并发冲突容错）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatGroupMemberMapper chatGroupMemberMapper;
    private final AttachmentService attachmentService;
    private final ChatSessionService chatSessionService;
    private final WsEventPublisher wsEventPublisher;

    /**
     * 保存一条聊天消息（支持 clientMessageId 幂等）
     *
     * 新增参数 clientMessageId：客户端生成的 UUID（可为空）。
     *
     * 行为：
     * - 若 clientMessageId 非空，先检查是否已存在由该 sender 发出的相同 clientMessageId 的记录；
     *   - 若存在，返回已有的发送者视角记录（避免重复插入）
     * - 否则按原逻辑插入：先插 sender view，再插 receiver(s) view
     *
     * 返回：
     * - 私聊：返回发送者视角的 ChatMessage（用于 WS 向发送方回发/前端直接使用）
     * - 群聊：返回发送者自己那条记录
     */
    @Transactional
    public ChatMessage saveMessage(String conversationType,
                                   Long fromUserId,
                                   Long toUserId,
                                   Long groupId,
                                   String messageType,
                                   String content,
                                   Long attachmentId,
                                   String clientMessageId) {

        // 幂等检查：如果 clientMessageId 非空并且已有记录，则直接返回发送者视角那条记录
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            List<ChatMessage> existing = chatMessageMapper.selectByFromAndClientId(fromUserId, clientMessageId);
            if (existing != null && !existing.isEmpty()) {
                // 找到发送者视角记录（to_user_id == fromUserId）优先返回
                for (ChatMessage m : existing) {
                    if (m.getToUserId() != null && m.getToUserId().equals(fromUserId)) {
                        return m;
                    }
                }
                // 没有明确的发送者视角，返回第一条以保证幂等性（后续逻辑视情况处理）
                return existing.get(0);
            }
        }

        if ("PRIVATE".equalsIgnoreCase(conversationType)) {
            if (toUserId == null) {
                throw new IllegalArgumentException("私聊消息的 toUserId 不能为空");
            }

            try {
                // 1) 插入发送者自己的视角记录
                ChatMessage senderView = new ChatMessage();
                senderView.setClientMessageId(clientMessageId);
                senderView.setConversationType("PRIVATE");
                senderView.setFromUserId(fromUserId);
                senderView.setToUserId(fromUserId);   // 自己视角
                senderView.setGroupId(null);
                senderView.setMessageType(messageType);
                senderView.setContent(content);
                senderView.setAttachmentId(attachmentId);
                senderView.setIsRead(1);              // 自己发出的，天然已读
                senderView.setDeletedAt(null);

                chatMessageMapper.insert(senderView);
                Long logicId = senderView.getId();
                senderView.setLogicMessageId(logicId);
                chatMessageMapper.updateById(senderView);

                // 2) 再为接收方插入一条视角记录
                ChatMessage receiverView = new ChatMessage();
                receiverView.setClientMessageId(clientMessageId);
                receiverView.setConversationType("PRIVATE");
                receiverView.setFromUserId(fromUserId);
                receiverView.setToUserId(toUserId);   // 接收方视角
                receiverView.setGroupId(null);
                receiverView.setMessageType(messageType);
                receiverView.setContent(content);
                receiverView.setAttachmentId(attachmentId);
                receiverView.setIsRead(0);            // 接收方初始未读
                receiverView.setDeletedAt(null);
                receiverView.setLogicMessageId(logicId);

                chatMessageMapper.insert(receiverView);

                // 3) 发送会话更新通知（SESSION_UPDATED）
                notifySessionNewMessageForPrivate(fromUserId, toUserId);

                // 返回发送者视角记录（WS、前端一般用这一条）
                return senderView;

            } catch (DuplicateKeyException dke) {
                // 并发场景下可能出现唯一索引冲突（from_user_id + client_message_id）
                // 捕获后回查已有记录并返回发送者视角，避免抛异常到上层
                log.warn("DuplicateKeyException when inserting private message (fromUserId={}, clientMessageId={}), attempting fallback query. Error: {}",
                        fromUserId, clientMessageId, dke.getMessage());
                if (clientMessageId != null && !clientMessageId.isBlank()) {
                    List<ChatMessage> existing = chatMessageMapper.selectByFromAndClientId(fromUserId, clientMessageId);
                    if (existing != null && !existing.isEmpty()) {
                        for (ChatMessage m : existing) {
                            if (m.getToUserId() != null && m.getToUserId().equals(fromUserId)) {
                                return m;
                            }
                        }
                        return existing.get(0);
                    }
                }
                // 回退查不到时，重新抛出以便上层处理（或记录）
                throw dke;
            }
        } else if ("GROUP".equalsIgnoreCase(conversationType)) {
            if (groupId == null) {
                throw new IllegalArgumentException("groupId 不能为空（群聊）");
            }

            try {
                // 1) 查询群成员
                List<Long> memberIds = chatGroupMemberMapper.listUserIdsByGroupId(groupId);
                if (memberIds == null || memberIds.isEmpty()) {
                    throw new IllegalArgumentException("群内没有成员，无法发送群消息");
                }

                // 2) 先为发送者自己插一条记录（方便返回值、逻辑ID）
                ChatMessage senderView = new ChatMessage();
                senderView.setClientMessageId(clientMessageId);
                senderView.setConversationType("GROUP");
                senderView.setFromUserId(fromUserId);
                senderView.setToUserId(fromUserId);  // 发送者自己的视角
                senderView.setGroupId(groupId);
                senderView.setMessageType(messageType);
                senderView.setContent(content);
                senderView.setAttachmentId(attachmentId);
                senderView.setIsRead(1);             // 自己发出的，视为已读
                senderView.setDeletedAt(null);

                chatMessageMapper.insert(senderView);
                Long logicId = senderView.getId();
                senderView.setLogicMessageId(logicId);
                chatMessageMapper.updateById(senderView);

                // 3) 为其它群成员插记录
                for (Long uid : memberIds) {
                    if (uid == null || uid.equals(fromUserId)) {
                        continue; // 已为发送者自己插过
                    }
                    ChatMessage m = new ChatMessage();
                    m.setClientMessageId(clientMessageId);
                    m.setConversationType("GROUP");
                    m.setFromUserId(fromUserId);
                    m.setToUserId(uid);         // 该成员视角
                    m.setGroupId(groupId);
                    m.setMessageType(messageType);
                    m.setContent(content);
                    m.setAttachmentId(attachmentId);
                    m.setIsRead(0);             // 其他成员初始未读
                    m.setDeletedAt(null);
                    m.setLogicMessageId(logicId);
                    chatMessageMapper.insert(m);
                }

                // 4) 群会话因新消息更新：给所有群成员推送会话更新事件
                for (Long uid : memberIds) {
                    if (uid == null) continue;
                    notifySessionNewMessageForGroup(uid, groupId);
                }

                return senderView;

            } catch (DuplicateKeyException dke) {
                // 并发冲突：回查已有记录（基于 fromUserId + clientMessageId），返回发送者视角
                log.warn("DuplicateKeyException when inserting group message (fromUserId={}, clientMessageId={}, groupId={}), attempting fallback query. Error: {}",
                        fromUserId, clientMessageId, groupId, dke.getMessage());
                if (clientMessageId != null && !clientMessageId.isBlank()) {
                    List<ChatMessage> existing = chatMessageMapper.selectByFromAndClientId(fromUserId, clientMessageId);
                    if (existing != null && !existing.isEmpty()) {
                        for (ChatMessage m : existing) {
                            if (m.getToUserId() != null && m.getToUserId().equals(fromUserId)) {
                                return m;
                            }
                        }
                        return existing.get(0);
                    }
                }
                throw dke;
            }
        } else {
            throw new IllegalArgumentException("未知的会话类型: " + conversationType);
        }
    }

    public ListPrivateMessagesResponse listPrivateMessages(ListPrivateMessagesRequest request, Long currentUserId) {
        Long friendId = request.getFriendId();
        List<ChatMessage> list = chatMessageMapper.listPrivateMessages(currentUserId, friendId,
                1000, 0); // 暂不分页，可以设一个最大数量
        List<ChatMessageDTO> dtos = list.stream().map(this::toDto).collect(Collectors.toList());

        ListPrivateMessagesResponse resp = new ListPrivateMessagesResponse();
        resp.setMessages(dtos);
        return resp;
    }

    public ListGroupMessagesResponse listGroupMessages(ListGroupMessagesRequest request, Long currentUserId) {
        Long groupId = request.getGroupId();
        List<ChatMessage> list = chatMessageMapper.listGroupMessages(groupId, currentUserId, 1000, 0);
        List<ChatMessageDTO> dtos = list.stream().map(this::toDto).collect(Collectors.toList());

        ListGroupMessagesResponse resp = new ListGroupMessagesResponse();
        resp.setMessages(dtos);
        return resp;
    }

    public DeleteMessageResponse deleteMessageForUser(DeleteMessageRequest request,
                                                      Long currentUserId) {
        Long messageId = request.getMessageId();
        if (messageId == null) {
            throw new IllegalArgumentException("messageId 不能为空");
        }

        int deleted = chatMessageMapper.deleteMessageForUser(currentUserId, messageId);

        DeleteMessageResponse resp = new DeleteMessageResponse();
        resp.setDeletedCount(deleted);
        return resp;
    }

    public MarkPrivateMessagesReadResponse markPrivateMessagesRead(MarkPrivateMessagesReadRequest request,
                                                                   Long currentUserId) {
        Long friendId = request.getFriendId();
        if (friendId == null) {
            throw new IllegalArgumentException("friendId 不能为空");
        }

        // 已读持久化
        int updated = chatMessageMapper.markPrivateMessagesRead(currentUserId, friendId);

        if (updated > 0) {
            try {
                // 1) 当前用户会话列表：读数清零（给自己推会话快照）
                /*notifySessionReadForPrivate(currentUserId, friendId);*/

                // 2) 查找已读到的最后一条消息 id（对方发给我的）
                Long lastReadMessageId = chatMessageMapper.findLastReadMessageIdBetween(currentUserId, friendId);

                if (lastReadMessageId != null) {
                    var payload = java.util.Map.of(
                            "conversationType", "PRIVATE",
                            "readerId", currentUserId,
                            "friendId", friendId,
                            "lastReadMessageId", lastReadMessageId
                    );
                    wsEventPublisher.sendToUser(friendId, SocketType.PRIVATE_MESSAGES_READ.toString(), payload);
                } else {
                    log.info("markPrivateMessagesRead: no lastReadMessageId found (maybe no messages from friend)");
                }
            } catch (Exception e) {
                log.warn("markPrivateMessagesRead notify failed, currentUserId={}, friendId={}, err={}",
                        currentUserId, friendId, e.getMessage(), e);
            }
        }

        MarkPrivateMessagesReadResponse resp = new MarkPrivateMessagesReadResponse();
        resp.setUpdatedCount(updated);
        return resp;
    }

    public MarkGroupMessagesReadResponse markGroupMessagesRead(MarkGroupMessagesReadRequest request,
                                                               Long currentUserId) {
        Long groupId = request.getGroupId();
        if (groupId == null) {
            throw new IllegalArgumentException("groupId 不能为空");
        }
        int updated = chatMessageMapper.markGroupMessagesRead(currentUserId, groupId);

        if (updated > 0) {
            try {
                // 当前用户在该群的未读数清零，用 GROUP_SESSION_READ 通知左侧列表刷新
                notifySessionReadForGroup(currentUserId, groupId);

            } catch (Exception e) {
                log.warn("markGroupMessagesRead notify failed, currentUserId={}, groupId={}, err={}",
                        currentUserId, groupId, e.getMessage());
            }
        }

        MarkGroupMessagesReadResponse resp = new MarkGroupMessagesReadResponse();
        resp.setUpdatedCount(updated);
        return resp;
    }

    /**
     * 将实体转换为历史消息 DTO，根据 attachmentId 生成 imageUrl
     */
    private ChatMessageDTO toDto(ChatMessage m) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(m.getId());
        dto.setConversationType(m.getConversationType());
        dto.setFromUserId(m.getFromUserId());
        dto.setToUserId(m.getToUserId());
        dto.setGroupId(m.getGroupId());
        dto.setMessageType(m.getMessageType());
        dto.setContent(m.getContent());
        dto.setCreatedAt(m.getCreatedAt());

        if ("IMAGE".equalsIgnoreCase(m.getMessageType()) && m.getAttachmentId() != null) {
            String url = attachmentService.generatePresignedGetUrl(m.getAttachmentId(), 3600);
            dto.setImageUrl(url);
        }
        return dto;
    }

    /**
     * 会话因“消息变化”（新消息、删除等）导致更新
     */
    private void notifySessionNewMessageForPrivate(Long userId, Long friendId) {
        try {
            SessionItem item = chatSessionService.buildPrivateSessionItem(userId, friendId);
            wsEventPublisher.sendToUser(userId, SocketType.NEW_PRIVATE_MESSAGE.toString(), item);
        } catch (Exception e) {
            log.warn("notifySessionMessageUpdatedForPrivate failed, userId={}, friendId={}, err={}",
                    userId, friendId, e.getMessage());
        }
    }

/*
    */
/**
     * 会话因“已读状态变化”导致更新
     *//*

    private void notifySessionReadForPrivate(Long userId, Long friendId) {
        try {
            SessionItem item = chatSessionService.buildPrivateSessionItem(userId, friendId);
            wsEventPublisher.sendToUser(userId, "MESSAGE_READ", item);
        } catch (Exception e) {
            log.warn("notifySessionReadUpdatedForPrivate failed, userId={}, friendId={}, err={}",
                    userId, friendId, e.getMessage());
        }
    }
*/

    /**
     * 群会话因“新消息”导致更新（给某个成员）
     */
    private void notifySessionNewMessageForGroup(Long userId, Long groupId) {
        try {
            SessionItem item = chatSessionService.buildGroupSessionItem(userId, groupId);
            wsEventPublisher.sendToUser(userId, SocketType.NEW_GROUP_MESSAGE.toString(), item);
        } catch (Exception e) {
            log.warn("notifySessionNewMessageForGroup failed, userId={}, groupId={}, err={}",
                    userId, groupId, e.getMessage());
        }
    }

    /**
     * 群会话因“已读状态变化”导致更新（给某个成员）
     * —— 目前先只对“当前读者自己”推，用于把自己的未读变 0
     */
    private void notifySessionReadForGroup(Long userId, Long groupId) {
        try {
            SessionItem item = chatSessionService.buildGroupSessionItem(userId, groupId);
            wsEventPublisher.sendToUser(userId, SocketType.GROUP_MESSAGES_READ.toString(), item);
        } catch (Exception e) {
            log.warn("notifySessionReadForGroup failed, userId={}, groupId={}, err={}",
                    userId, groupId, e.getMessage());
        }
    }

}