package com.anime.common.dto.chat.message;

import lombok.Data;

/**
 * 单向删除消息请求
 */
@Data
public class DeleteMessageRequest {

    /**
     * 要删除的消息ID（chat_messages.id）
     */
    private Long messageId;
}
