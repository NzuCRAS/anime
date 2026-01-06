package com.anime.common.dto.video;

import lombok.Data;

/**
 * ABR / 播放质量上报 DTO
 */
@Data
public class AbrReportRequest {
    private String sessionUuid;
    private Long videoId;
    private Long playDurationMs;
    private Integer avgBitrate;
    private Integer startupDelayMs;
    private Integer rebufferCount;
    private Integer totalRebufferMs;
    private Integer playTimestamp; // optional
    private String extra; // 可选 JSON 字符串
}