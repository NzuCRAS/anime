package com.anime.common.dto.video;

import lombok.Data;

import java.util.List;

@Data
public class PlayUrlRequest {
    /**
     * 要播放的附件 id 列表（每个代表一种清晰度或一种播放源）
     * 例如：[ 101, 102 ] 分别对应 1080p, 720p
     */
    private List<Long> attachmentIds;

    /**
     * 预签名 URL 的有效期（秒），默认 300（5 分钟）
     */
    private Integer expirySeconds;
}