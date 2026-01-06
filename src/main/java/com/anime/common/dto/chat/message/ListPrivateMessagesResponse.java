package com.anime.common.dto.chat.message;

import lombok.Data;

import java.util.List;

/**
 * 私聊历史消息响应
 */
@Data
public class ListPrivateMessagesResponse {

    /**
     * 与指定好友的所有历史消息（按时间排序）
     */
    private List<ChatMessageDTO> messages;
}