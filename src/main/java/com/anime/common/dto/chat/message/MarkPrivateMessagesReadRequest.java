package com.anime.common.dto.chat.message;

import lombok.Data;

/**
 * 标记与某好友的私聊消息为已读 请求
 */
@Data
public class MarkPrivateMessagesReadRequest {

    /**
     * 好友用户ID（对方）
     */
    private Long friendId;
}