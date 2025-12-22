package com.anime.common.dto.collection.leve1;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data

public class Leve1CreateDTO {
    @Schema(description = "引用的 attachment id（image 类型时使用）", example = "456")
    Long attachmentId;
    @Schema(description = "一级收藏夹名字", example = "默认收藏夹")
    String name;
}
