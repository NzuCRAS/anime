package com.anime.common.dto.chat.message;

import lombok.Data;

/**
 * 标记私聊消息为已读 响应
 */
@Data
public class MarkPrivateMessagesReadResponse {

    /**
     * 本次被更新为已读的消息条数
     */
    private Integer updatedCount;
}