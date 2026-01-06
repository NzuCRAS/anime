package com.anime.chat.service;

import com.anime.common.dto.chat.message.*;
import com.anime.common.entity.chat.ChatMessage;
import com.anime.common.mapper.chat.ChatGroupMemberMapper;
import com.anime.common.mapper.chat.ChatMessageMapper;
import com.anime.common.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天消息业务逻辑
 */
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatGroupMemberMapper chatGroupMemberMapper;
    private final AttachmentService attachmentService;

    /**
     * 保存一条聊天消息。
     *
     * 模型：
     * - 每条记录代表「某个接收方视角的一条消息」；
     * - 私聊：为发送方和接收方各插一条记录：
     *   - 发送方视角：to_user_id = 发送方, is_read = 1
     *   - 接收方视角：to_user_id = 接收方, is_read = 0
     * - 群聊：为群内每个成员插一条记录（to_user_id=成员ID）。
     *   这样 deleted_at / is_read 都是 per-user 生效，不影响别人。
     *
     * 返回值：
     * - 私聊：返回发送方视角记录（方便前端直接显示“我发出的这条”）
     * - 群聊：返回发送者自己的那条群消息记录
     */
    public ChatMessage saveMessage(String conversationType,
                                   Long fromUserId,
                                   Long toUserId,
                                   Long groupId,
                                   String messageType,
                                   String content,
                                   Long attachmentId) {

        if ("PRIVATE".equalsIgnoreCase(conversationType)) {
            if (toUserId == null) {
                throw new IllegalArgumentException("私聊消息的 toUserId 不能为空");
            }

            // 1) 先插入发送者自己的视角记录
            ChatMessage senderView = new ChatMessage();
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

            // 返回发送者视角记录（WS、前端一般用这一条）
            return senderView;

        } else if ("GROUP".equalsIgnoreCase(conversationType)) {
            if (groupId == null) {
                throw new IllegalArgumentException("groupId 不能为空（群聊）");
            }

            // 1) 查询群成员
            List<Long> memberIds = chatGroupMemberMapper.listUserIdsByGroupId(groupId);
            if (memberIds == null || memberIds.isEmpty()) {
                throw new IllegalArgumentException("群内没有成员，无法发送群消息");
            }

            // 2) 先为发送者自己插一条记录（方便返回值、逻辑ID）
            ChatMessage senderView = new ChatMessage();
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

            return senderView;
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

        int updated = chatMessageMapper.markPrivateMessagesRead(currentUserId, friendId);

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
}