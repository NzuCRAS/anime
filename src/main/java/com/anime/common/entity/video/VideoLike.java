package com.anime.common.entity.video;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_likes")
public class VideoLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long videoId;

    private Long userId;

    /**
     * active: 1 = liked, 0 = not liked (soft)
     */
    private Integer active;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}