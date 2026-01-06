package com.anime.common.dto.chat.session;

import lombok.Data;

/**
 * 获取会话列表请求
 * 暂无过滤条件，预留扩展字段
 */
@Data
public class ListSessionsRequest {
    // 目前无需任何字段，将来可以加过滤条件，例如仅未读、有消息的会话等
}