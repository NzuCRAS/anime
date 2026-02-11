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

    /**
     * 是否已读（对该接收方）：0=未读，1=已读
     *
     * 私聊中：
     * - 当 fromUserId 是“我”，toUserId 是“对方”时，isRead 表示对方是否已读我发的消息
     * - 当 fromUserId 是“对方”，toUserId 是“我”时，isRead 表示我是否已读对方的消息
     */
    private Integer isRead;
}
