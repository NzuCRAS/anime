package com.anime.common.dto.video;

import lombok.Data;

@Data
public class PlayUrlItem {
    private Long attachmentId;
    private String url;
    private String mimeType;
    private String qualityLabel; // 可选：前端可用来显示 "1080p"、"720p"
}