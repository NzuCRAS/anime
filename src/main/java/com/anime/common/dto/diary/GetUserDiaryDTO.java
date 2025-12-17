package com.anime.common.dto.diary;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Diary 简要信息（用于列表展示）：只包含 id / title / createdAt
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户日记摘要（列表项）")
public class GetUserDiaryDTO {
    @Schema(description = "日记ID", example = "123")
    private Long id;

    @Schema(description = "日记标题", example = "我的旅行日记")
    private String title;

    @Schema(description = "创建时间", example = "2025-12-17T10:00:00")
    private LocalDateTime createdAt;
}