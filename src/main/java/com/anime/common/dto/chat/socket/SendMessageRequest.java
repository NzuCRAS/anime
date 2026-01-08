package com.anime.common.dto.chat.socket;

import lombok.Data;

/**
 * WebSocket 发送聊天消息请求（客户端 -> 服务器）
 */
@Data
public class SendMessageRequest {

    /**
     * 会话类型：
     * - "PRIVATE"：单聊
     * - "GROUP"：群聊
     */
    private String conversationType;

    /**
     * 目标用户ID（单聊时必填；群聊时置为 null）
     */
    private Long targetUserId;

    /**
     * 目标群ID（群聊时必填；单聊时置为 null）
     */
    private Long groupId;

    /**
     * 消息类型：
     * - "TEXT"
     * - "IMAGE"
     */
    private String messageType;

    /**
     * 文本内容（TEXT 消息时使用）
     */
    private String content;

    /**
     * 图片附件ID（IMAGE 消息时使用）；
     * 前端先通过 presign 上传，拿到 attachmentId。
     */
    private Long attachmentId;

    /**
     * 用于幂等性检验
     */
    private String clientMessageId;
}
