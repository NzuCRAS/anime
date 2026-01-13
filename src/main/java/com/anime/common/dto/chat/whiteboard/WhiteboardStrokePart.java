package com.anime.common.dto.chat.whiteboard;

import lombok.Data;

import java.util.List;

/**
 * 白板笔画分片（batch points）
 * points 格式建议为 [[xNorm,yNorm],[xNorm,yNorm], ...]（normalized in 0..1）
 */
@Data
public class WhiteboardStrokePart {
    private Long targetUserId;
    private String boardId;
    private String strokeId;
    private String tool; // "pen" / "eraser" ...
    private String color;
    private Integer width;
    private List<List<Double>> points;
    private Boolean isEnd; // 该笔画是否结束
    private Long ts;
}