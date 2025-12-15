package com.anime.common.dto.attachment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PresignRequestDTO {
    // 文件原始名称(形如"test.jpg")
    private String originFileName;

    // 文件拓展名(形如"image/jpeg")
    private String mimeType;
}
