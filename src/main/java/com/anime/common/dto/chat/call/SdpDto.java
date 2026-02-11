package com.anime.common.dto.chat.call;

import lombok.Data;

/**
 * SDP 载体：包含 sdp 文本和 type ("offer" / "answer")
 */
@Data
public class SdpDto {
    private String sdp;
    private String type;
}