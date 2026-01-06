package com.anime.common.entity.video;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_transcodes")
public class VideoTranscode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 视频ID（videos.id）
     */
    private Long videoId;

    /**
     * 表示清晰度，如 240p / 480p / 720p
     */
    private String representationId;

    /**
     * 码率（bps）
     */
    private Integer bitrate;

    /**
     * 分辨率，如 854x480
     */
    private String resolution;

    /**
     * 子playlist或DASH路径
     */
    private String manifestPath;

    /**
     * 分片路径前缀
     */
    private String segmentBasePath;

    /**
     * processing / ready / failed
     */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}