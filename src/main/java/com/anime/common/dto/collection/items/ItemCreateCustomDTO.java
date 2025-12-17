package com.anime.common.dto.collection.items;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建自定义收藏项请求（带附件/名称/描述）")
public class ItemCreateCustomDTO {
    @Schema(description = "封面附件ID", example = "789")
    private Long attachment_id;

    @Schema(description = "名称", example = "我的收藏")
    private String name;

    @Schema(description = "描述", example = "这是一段描述")
    private String description;

    @Schema(description = "父级二级收藏夹ID", example = "12")
    private Long father_level2_id;
}