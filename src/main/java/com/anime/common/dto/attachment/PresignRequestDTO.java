package com.anime.common.dto.attachment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "预签名请求：请求后端生成 presigned PUT URL")
public class PresignRequestDTO {
    @Schema(description = "文件原始名（例如 test.jpg）", example = "test.jpg")
    private String originalFilename;

    @Schema(description = "MIME 类型（例如 image/jpeg）", example = "image/jpeg")
    private String mimeType;
}