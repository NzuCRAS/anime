package com.anime.common.dto.collection.items;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "删除收藏项请求")
public class ItemDeleteDTO {
    @Schema(description = "收藏项ID", example = "123")
    Long id;
}