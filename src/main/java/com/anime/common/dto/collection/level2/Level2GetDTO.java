package com.anime.common.dto.collection.level2;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class Level2GetDTO {
    @Schema(description = "父级一级收藏夹ID", example = "1")
    Long father_id;
}
