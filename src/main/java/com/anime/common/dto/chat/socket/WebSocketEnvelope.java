package com.anime.common.dto.chat.socket;

import lombok.Data;

/**
 * WebSocket 消息统一封装，便于支持多种消息类型。
 */
@Data
public class WebSocketEnvelope<T> {

    /**
     * 消息类型：
     * - "SEND_MESSAGE"：客户端发送聊天消息
     * - "NEW_MESSAGE"：服务器推送聊天消息
     * - 后续可以扩展：PING、ACK 等
     */
    private String type;

    /**
     * 实际业务负载
     */
    private T payload;
}