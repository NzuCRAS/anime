package com.anime.video.service;

import com.anime.common.dto.video.AbrReportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 简易 ABR 指标入库服务（使用 JdbcTemplate 快速写入）
 */
@Service
@RequiredArgsConstructor
public class AbrService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 将上报的数据写入 abr_sessions 和 abr_session_metrics（简化版）
     */
    public void reportMetrics(AbrReportRequest req, Long reporterUserId) {
        // Insert abr_sessions (if not exists) - here we insert a new session per report for simplicity
        String insertSession = "INSERT INTO abr_sessions (session_uuid, user_id, video_id, strategy, started_at) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(insertSession, req.getSessionUuid(), reporterUserId, req.getVideoId(), "CLIENT_ONLY", LocalDateTime.now());

        String insertMetrics = "INSERT INTO abr_session_metrics (session_id, avg_bitrate, startup_delay_ms, rebuffer_count, total_rebuffer_ms, play_duration_ms, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        // Get the last inserted session id (MySQL LAST_INSERT_ID) — here we simplifiy by selecting id from abr_sessions by uuid
        Long sessionId = jdbcTemplate.queryForObject("SELECT id FROM abr_sessions WHERE session_uuid = ?", Long.class, req.getSessionUuid());
        jdbcTemplate.update(insertMetrics,
                sessionId,
                req.getAvgBitrate(),
                req.getStartupDelayMs(),
                req.getRebufferCount(),
                req.getTotalRebufferMs(),
                req.getPlayDurationMs(),
                LocalDateTime.now());
    }
}