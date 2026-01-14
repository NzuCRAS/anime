package com.anime.common.dto.chat.message;

import lombok.Data;

/**
 * 撤回消息请求
 */
@Data
public class RecallMessageRequest {

    /**
     * 要撤回的消息ID（chat_messages.id，任意该逻辑消息下的一条记录均可）
     */
    private Long messageId;
}