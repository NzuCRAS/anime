package com.anime.common.dto.collection.items;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新收藏项封面请求")
public class ItemUpdateCoverDTO {
    @Schema(description = "收藏项ID", example = "123")
    Long id;

    @Schema(description = "新的封面 attachment id", example = "789")
    Long attachment_id;
}