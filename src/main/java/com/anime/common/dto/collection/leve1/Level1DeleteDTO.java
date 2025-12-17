package com.anime.common.dto.collection.leve1;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "删除一级收藏夹请求")
public class Level1DeleteDTO {
    @Schema(description = "一级收藏夹ID", example = "1")
    Long id;
}