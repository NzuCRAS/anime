package com.anime.common.dto.chat.message;

import lombok.Data;

/**
 * 获取某个群的历史消息请求
 */
@Data
public class ListGroupMessagesRequest {

    /**
     * 群ID
     */
    private Long groupId;
}