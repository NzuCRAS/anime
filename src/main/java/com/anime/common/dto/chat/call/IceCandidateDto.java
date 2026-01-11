package com.anime.common.dto.chat.call;

import lombok.Data;

/**
 * ICE candidate 传输
 */
@Data
public class IceCandidateDto {
    private String callId;
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;
}