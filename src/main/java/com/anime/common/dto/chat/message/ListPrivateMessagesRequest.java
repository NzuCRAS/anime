package com.anime.common.dto.chat.message;

import lombok.Data;

/**
 * 获取与某好友的历史私聊消息请求
 */
@Data
public class ListPrivateMessagesRequest {

    /**
     * 好友用户ID
     */
    private Long friendId;
}