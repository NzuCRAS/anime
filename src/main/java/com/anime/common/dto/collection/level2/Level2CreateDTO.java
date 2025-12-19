package com.anime.common.dto.collection.level2;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建或获取二级收藏夹请求（需要父级一级收藏夹ID）")
public class Level2CreateDTO {
    @Schema(description = "二级收藏夹名字name", example = "二级收藏夹")
    String name;
    @Schema(description = "父级一级收藏夹ID", example = "1")
    Long father_id;
}