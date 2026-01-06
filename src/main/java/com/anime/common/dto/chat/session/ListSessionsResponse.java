package com.anime.common.dto.chat.session;

import lombok.Data;

import java.util.List;

/**
 * 获取会话列表响应
 */
@Data
public class ListSessionsResponse {

    /**
     * 当前用户所有会话（单聊 + 群聊），
     * 已按 lastMessageTime 从新到旧排序
     */
    private List<SessionItem> sessions;
}