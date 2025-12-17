package com.anime.common.dto.collection.items;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新收藏项描述请求")
public class ItemUpdateDescriptionDTO {
    @Schema(description = "收藏项ID", example = "123")
    Long id;

    @Schema(description = "新的描述", example = "更新后的描述")
    String description;
}