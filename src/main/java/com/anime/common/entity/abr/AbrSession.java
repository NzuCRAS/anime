package com.anime.common.entity.abr;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("abr_sessions")
public class AbrSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 前端/后端生成的会话唯一标识
     */
    private String sessionUuid;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * ABR策略，如 CLIENT_ONLY / SERVER_ASSISTED
     */
    private String strategy;

    /**
     * 拥塞模式，如 DIRECT_STREAM / CACHE_PRIORITY（可选）
     */
    private String congestionMode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
}