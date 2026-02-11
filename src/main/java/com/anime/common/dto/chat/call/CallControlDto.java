package com.anime.common.dto.chat.call;

import lombok.Data;

/**
 * 通用控制类（挂断 / 拒绝 / 接受 等）
 */
@Data
public class CallControlDto {
    private String callId;
    private String reason; // optional
}