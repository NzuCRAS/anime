package com.anime.common.dto.collection.leve1;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新一级收藏夹名称请求")
public class Level1UpdateNameDTO {
    @Schema(description = "一级收藏夹ID", example = "1")
    Long id;

    @Schema(description = "新的名称", example = "默认收藏夹")
    String name;
}