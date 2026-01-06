package com.anime.common.entity.abr;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("abr_session_metrics")
public class AbrSessionMetrics {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID（abr_sessions.id）
     */
    private Long sessionId;

    /**
     * 平均播放码率（bps）
     */
    private Integer avgBitrate;

    /**
     * 启动延迟ms
     */
    private Integer startupDelayMs;

    /**
     * 重缓冲次数
     */
    private Integer rebufferCount;

    /**
     * 总重缓冲时长ms
     */
    private Integer totalRebufferMs;

    /**
     * 播放时长ms
     */
    private Integer playDurationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}