package com.anime.common.dto.chat.call;

import lombok.Data;

/**
 * 客户端发送的呼叫邀请（offer）
 */
@Data
public class CallInviteRequest {
    // 目标 userId（必填）
    private Long targetUserId;
    // 可选：客户端生成的 callId（如果为空，服务端会生成一个）
    private String callId;
    // SDP offer（base64 或原始字符串）
    private SdpDto sdp;
    // 可选：约定的媒体配置或其它元数据（比如 video/audio flags）
    private String metadata;
}