package com.anime.common.dto.chat.message;

import lombok.Data;

import java.util.List;

/**
 * 群聊历史消息响应
 */
@Data
public class ListGroupMessagesResponse {

    /**
     * 指定群的所有历史消息（按时间排序）
     */
    private List<ChatMessageDTO> messages;
}
