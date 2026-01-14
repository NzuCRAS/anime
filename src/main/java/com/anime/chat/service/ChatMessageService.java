package com.anime.chat.service;

import com.anime.chat.socket.WsEventPublisher;
import com.anime.common.dto.chat.message.*;
import com.anime.common.dto.chat.session.SessionItem;
import com.anime.common.entity.attachment.Attachment;
import com.anime.common.entity.chat.ChatMessage;
import com.anime.common.enums.SocketType;
import com.anime.common.mapper.chat.ChatGroupMemberMapper;
import com.anime.common.mapper.chat.ChatMessageMapper;
import com.anime.common.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 聊天消息业务逻辑（含：幂等、删除、撤回）
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

    private static final Duration RECALL_WINDOW = Duration.ofMinutes(3);

    @Transactional
    public ChatMessage saveMessage(String conversationType,
                                   Long fromUserId,
                                   Long toUserId,
                                   Long groupId,
                                   String messageType,
                                   String content,
                                   Long attachmentId,
                                   String clientMessageId) {

        if (attachmentId != null) {
            Attachment a = attachmentService.getAttachmentById(attachmentId);
            attachmentService.completeUpload(attachmentId);
        }

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

        if ("PRIVATE".equalsIgnoreCase(conversationType)) {
            if (toUserId == null) {
                throw new IllegalArgumentException("私聊消息的 toUserId 不能为空");
            }

            try {
                ChatMessage senderView = new ChatMessage();
                senderView.setClientMessageId(clientMessageId);
                senderView.setConversationType("PRIVATE");
                senderView.setFromUserId(fromUserId);
                senderView.setToUserId(fromUserId);
                senderView.setGroupId(null);
                senderView.setMessageType(messageType);
                senderView.setContent(content);
                senderView.setAttachmentId(attachmentId);
                senderView.setIsRead(1);
                senderView.setDeletedAt(null);

                chatMessageMapper.insert(senderView);
                Long logicId = senderView.getId();
                senderView.setLogicMessageId(logicId);
                chatMessageMapper.updateById(senderView);

                ChatMessage receiverView = new ChatMessage();
                receiverView.setClientMessageId(clientMessageId);
                receiverView.setConversationType("PRIVATE");
                receiverView.setFromUserId(fromUserId);
                receiverView.setToUserId(toUserId);
                receiverView.setGroupId(null);
                receiverView.setMessageType(messageType);
                receiverView.setContent(content);
                receiverView.setAttachmentId(attachmentId);
                receiverView.setIsRead(0);
                receiverView.setDeletedAt(null);
                receiverView.setLogicMessageId(logicId);

                chatMessageMapper.insert(receiverView);

                return senderView;

            } catch (DuplicateKeyException dke) {
                log.warn("DuplicateKeyException when inserting private message (fromUserId={}, clientMessageId={})",
                        fromUserId, clientMessageId, dke);
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
        } else if ("GROUP".equalsIgnoreCase(conversationType)) {
            if (groupId == null) {
                throw new IllegalArgumentException("groupId 不能为空（群聊）");
            }

            try {
                List<Long> memberIds = chatGroupMemberMapper.listUserIdsByGroupId(groupId);
                if (memberIds == null || memberIds.isEmpty()) {
                    throw new IllegalArgumentException("群内没有成员，无法发送群消息");
                }

                ChatMessage senderView = new ChatMessage();
                senderView.setClientMessageId(clientMessageId);
                senderView.setConversationType("GROUP");
                senderView.setFromUserId(fromUserId);
                senderView.setToUserId(fromUserId);
                senderView.setGroupId(groupId);
                senderView.setMessageType(messageType);
                senderView.setContent(content);
                senderView.setAttachmentId(attachmentId);
                senderView.setIsRead(1);
                senderView.setDeletedAt(null);

                chatMessageMapper.insert(senderView);
                Long logicId = senderView.getId();
                senderView.setLogicMessageId(logicId);
                chatMessageMapper.updateById(senderView);

                for (Long uid : memberIds) {
                    if (uid == null || uid.equals(fromUserId)) {
                        continue;
                    }
                    ChatMessage m = new ChatMessage();
                    m.setClientMessageId(clientMessageId);
                    m.setConversationType("GROUP");
                    m.setFromUserId(fromUserId);
                    m.setToUserId(uid);
                    m.setGroupId(groupId);
                    m.setMessageType(messageType);
                    m.setContent(content);
                    m.setAttachmentId(attachmentId);
                    m.setIsRead(0);
                    m.setDeletedAt(null);
                    m.setLogicMessageId(logicId);
                    chatMessageMapper.insert(m);
                }

                // 会话更新推送（保持原逻辑）
                for (Long uid : memberIds) {
                    if (uid == null) continue;
                    notifySessionNewMessageForGroup(uid, groupId);
                }

                return senderView;

            } catch (DuplicateKeyException dke) {
                log.warn("DuplicateKeyException when inserting group message (fromUserId={}, clientMessageId={}, groupId={})",
                        fromUserId, clientMessageId, groupId, dke);
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
                50, (request.getPage() > 0 ? request.getPage() : 0) * 50);
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

    /**
     * 单向删除：仅删除当前用户视角的该条消息
     */
    public DeleteMessageResponse deleteMessageForUser(DeleteMessageRequest request,
                                                      Long currentUserId) {
        Long messageId = request.getMessageId();
        if (messageId == null) {
            throw new IllegalArgumentException("messageId 不能为空");
        }

        int deleted = chatMessageMapper.deleteMessageForUser(currentUserId, messageId);

        // 可选：通知当前用户前端移除该消息
        if (deleted > 0) {
            try {
                var payload = java.util.Map.of("messageId", messageId);
                wsEventPublisher.sendToUser(currentUserId, "MESSAGE_DELETED", payload);
            } catch (Exception e) {
                log.warn("notify MESSAGE_DELETED failed userId={} messageId={} err={}", currentUserId, messageId, e.getMessage());
            }
        }

        DeleteMessageResponse resp = new DeleteMessageResponse();
        resp.setDeletedCount(deleted);
        return resp;
    }

    /**
     * 撤回消息：仅允许发送者在 3 分钟内撤回。
     * 行为：将该逻辑消息下的所有记录（所有接收者 + 发送者视角）逻辑删除。
     */
    @Transactional
    public RecallMessageResponse recallMessage(RecallMessageRequest request, Long currentUserId) {
        RecallMessageResponse resp = new RecallMessageResponse();
        if (request == null || request.getMessageId() == null) {
            throw new IllegalArgumentException("messageId 不能为空");
        }

        ChatMessage anyRecord = chatMessageMapper.selectById(request.getMessageId());
        if (anyRecord == null) {
            resp.setAllowed(false);
            resp.setRecalledCount(0);
            resp.setReason("message_not_found");
            return resp;
        }

        // 必须是发送者本人
        if (anyRecord.getFromUserId() == null || !anyRecord.getFromUserId().equals(currentUserId)) {
            resp.setAllowed(false);
            resp.setRecalledCount(0);
            resp.setReason("not_message_sender");
            return resp;
        }

        // 必须在时间窗口内
        LocalDateTime created = anyRecord.getCreatedAt();
        if (created == null || LocalDateTime.now().isAfter(created.plus(RECALL_WINDOW))) {
            resp.setAllowed(false);
            resp.setRecalledCount(0);
            resp.setReason("recall_window_expired");
            return resp;
        }

        Long logicId = (anyRecord.getLogicMessageId() != null) ? anyRecord.getLogicMessageId() : anyRecord.getId();

        int updated = chatMessageMapper.recallByLogicId(logicId);
        resp.setAllowed(true);
        resp.setRecalledCount(updated);
        resp.setReason(null);

        // 事务提交后，通知所有相关用户，让前端移除该逻辑消息
        final Long logicMessageIdFinal = logicId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    List<Long> recipients = chatMessageMapper.listRecipientsByLogicId(logicMessageIdFinal);
                    if (recipients != null) {
                        for (Long uid : recipients) {
                            if (uid == null) continue;
                            var payload = java.util.Map.of(
                                    "logicMessageId", logicMessageIdFinal,
                                    "conversationType", anyRecord.getConversationType(),
                                    "senderId", anyRecord.getFromUserId()
                            );
                            wsEventPublisher.sendToUser(uid, "MESSAGE_RECALLED", payload);
                        }
                    }
                } catch (Exception e) {
                    log.warn("notify MESSAGE_RECALLED failed logicId={} err={}", logicMessageIdFinal, e.getMessage());
                }
            }
        });

        return resp;
    }

    public MarkPrivateMessagesReadResponse markPrivateMessagesRead(MarkPrivateMessagesReadRequest request,
                                                                   Long currentUserId) {
        Long friendId = request.getFriendId();
        if (friendId == null) {
            throw new IllegalArgumentException("friendId 不能为空");
        }

        int updated = chatMessageMapper.markPrivateMessagesRead(currentUserId, friendId);

        if (updated > 0) {
            try {
                Long lastReadMessageId = chatMessageMapper.findLastReadMessageIdBetween(currentUserId, friendId);
                if (lastReadMessageId != null) {
                    var payload = java.util.Map.of(
                            "conversationType", "PRIVATE",
                            "readerId", currentUserId,
                            "friendId", friendId,
                            "lastReadMessageId", lastReadMessageId
                    );
                    wsEventPublisher.sendToUser(friendId, SocketType.PRIVATE_MESSAGES_READ.toString(), payload);
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

    public ChatMessageDTO toDto(ChatMessage m) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(m.getId());
        dto.setConversationType(m.getConversationType());
        dto.setFromUserId(m.getFromUserId());
        dto.setToUserId(m.getToUserId());
        dto.setGroupId(m.getGroupId());
        dto.setMessageType(m.getMessageType());
        dto.setContent(m.getContent());
        dto.setCreatedAt(m.getCreatedAt());

        if (!Objects.equals(m.getMessageType(), "TEXT") && m.getAttachmentId() != null) {
            String url = attachmentService.generatePresignedGetUrl(m.getAttachmentId(), 3600);
            dto.setFileUrl(url);
        }
        return dto;
    }

    private void notifySessionNewMessageForGroup(Long userId, Long groupId) {
        try {
            SessionItem item = chatSessionService.buildGroupSessionItem(userId, groupId);
            wsEventPublisher.sendToUser(userId, SocketType.NEW_GROUP_MESSAGE.toString(), item);
        } catch (Exception e) {
            log.warn("notifySessionNewMessageForGroup failed, userId={}, groupId={}, err={}",
                    userId, groupId, e.getMessage());
        }
    }

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