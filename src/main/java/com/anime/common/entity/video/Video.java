package com.anime.common.entity.video;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("videos")
public class Video {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 上传者ID（users.id）
     */
    private Long uploaderId;

    private String title;

    private String description;

    /**
     * 原始 MP4 在 attachments 表的ID
     */
    private Long sourceAttachmentId;

    /**
     * 封面图 attachments.id
     */
    private Long coverAttachmentId;

    /**
     * 状态：uploading / processing / ready / failed
     */
    private String status;

    /**
     * 视频时长（秒）
     */
    private Integer durationSec;

    /**
     * 点赞数缓存
     */
    private Integer likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}