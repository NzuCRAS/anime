package com.anime.common.dto.collection.leve1;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class Level1ResultDTO {
    @Schema(description = "收藏夹1封面的url")
    String URL;

    @Schema(description = "收藏夹1的名字")
    String name;

    @Schema(description = "收藏夹1的id")
    Long id;
}
