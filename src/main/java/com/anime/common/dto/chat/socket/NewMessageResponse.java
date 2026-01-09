package com.anime.common.dto.chat.socket;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * WebSocket 推送的新消息（服务器 -> 客户端）
 */
@Data
public class NewMessageResponse {

    /**
     * 消息ID（chat_messages.id）
     */
    private Long id;

    /**
     * 会话类型：PRIVATE / GROUP
     */
    private String conversationType;

    /**
     * 发送者用户ID
     */
    private Long fromUserId;

    /**
     * 接收者用户ID（仅 PRIVATE）
     */
    private Long toUserId;

    /**
     * 群ID（仅 GROUP）
     */
    private Long groupId;

    /**
     * 消息类型：TEXT / IMAGE / 其他
     */
    private String messageType;

    /**
     * 文本内容
     */
    private String content;

    /**
     * 文件 URL
     */
    private String fileUrl;

    /**
     * 发送时间
     */
    private LocalDateTime createdAt;
}
