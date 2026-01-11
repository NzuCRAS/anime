package com.anime.common.dto.chat.call;

import lombok.Data;

/**
 * 客户端对邀请的应答（answer）
 */
@Data
public class CallAnswerRequest {
    private String callId;
    private SdpDto sdp;
}