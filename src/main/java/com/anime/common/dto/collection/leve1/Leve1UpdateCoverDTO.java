package com.anime.common.dto.collection.leve1;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新一级收藏夹封面请求")
public class Leve1UpdateCoverDTO {
    @Schema(description = "一级收藏夹ID", example = "1")
    Long id;
    @Schema(description = "新的封面 attachment id", example = "789")
    Long attachment_id;

}
