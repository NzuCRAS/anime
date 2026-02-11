package com.anime.common.dto.chat.message;

import lombok.Data;

/**
 * 撤回消息响应
 */
@Data
public class RecallMessageResponse {

    /**
     * 是否允许撤回（校验通过）
     */
    private boolean allowed;

    /**
     * 实际被标记撤回（逻辑删除）的记录数
     */
    private Integer recalledCount;

    /**
     * 如果不允许或失败，返回原因
     */
    private String reason;
}