package com.anime.common.dto.video;

import lombok.Data;

/**
 * 前端提交的视频创建请求（在完成上传并填写信息后调用）
 */
@Data
public class CreateVideoRequest {
    private Long uploaderId;
    private Long sourceAttachmentId;
    private Long coverAttachmentId;
    private String title;
    private String description;
}