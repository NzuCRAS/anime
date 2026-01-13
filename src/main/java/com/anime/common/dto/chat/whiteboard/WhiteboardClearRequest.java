package com.anime.common.dto.chat.whiteboard;

import lombok.Data;

/**
 * 清空白板请求
 */
@Data
public class WhiteboardClearRequest {
    private Long targetUserId;
    private Long ts;
}