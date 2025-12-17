package com.anime.common.dto.collection.items;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新收藏项名称请求")
public class ItemUpdateNameDTO {
    @Schema(description = "收藏项ID", example = "123")
    Long id;

    @Schema(description = "新的名称", example = "新名称")
    String name;
}