package com.anime.common.dto.collection.items;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建默认收藏项请求（仅描述，可选）")
public class ItemCreateDefaultDTO {
    @Schema(description = "描述", example = "默认描述")
    private String description;

    @Schema(description = "父级二级收藏夹ID", example = "12")
    private Long father_level2_id;
}