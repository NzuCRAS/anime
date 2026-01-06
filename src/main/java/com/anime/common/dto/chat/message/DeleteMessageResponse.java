package com.anime.common.dto.chat.message;

import lombok.Data;

/**
 * 单向删除消息响应
 */
@Data
public class DeleteMessageResponse {

    /**
     * 实际删除的记录数（0 或 1）
     */
    private Integer deletedCount;
}
