package com.anime.common.dto.collection.level2;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "删除二级收藏夹请求")
public class Level2DeleteDTO {
    @Schema(description = "二级收藏夹ID", example = "2")
    Long id;
}