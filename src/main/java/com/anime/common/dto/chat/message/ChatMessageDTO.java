package com.anime.common.dto.chat.message;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史消息中的单条消息
 */
@Data
public class ChatMessageDTO {

    /**
     * 消息ID
     */
    private Long id;

    /**
     * 会话类型：PRIVATE / GROUP
     */
    private String conversationType;

    /**
     * 发送者ID
     */
    private Long fromUserId;

    /**
     * 接收者ID（仅在 PRIVATE 会话中使用）
     */
    private Long toUserId;

    /**
     * 群ID（仅在 GROUP 会话中使用）
     */
    private Long groupId;

    /**
     * 消息类型：TEXT / IMAGE / 其他
     */
    private String messageType;

    /**
     * 文本内容（TEXT 消息）
     */
    private String content;

    /**
     * 文件 URL（非文本消息）
     */
    private String fileUrl;

    /**
     * 发送时间
     */
    private LocalDateTime createdAt;
}
