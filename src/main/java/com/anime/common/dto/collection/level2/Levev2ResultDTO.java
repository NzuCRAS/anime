package com.anime.common.dto.collection.level2;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class Levev2ResultDTO {
    @Schema(description = "收藏夹2的名字")
    String name;

    @Schema(description = "收藏夹2的id")
    Long id;

    @Schema(description = "收藏夹2的父文件夹id")
    Long  father_id;
}
