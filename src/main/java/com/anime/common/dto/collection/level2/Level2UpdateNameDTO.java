package com.anime.common.dto.collection.level2;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新二级收藏夹名称请求")
public class Level2UpdateNameDTO {
    @Schema(description = "二级收藏夹ID", example = "2")
    Long id;

    @Schema(description = "新的名称", example = "我的子文件夹")
    String name;
}