package com.anime.common.dto.diary;

import com.anime.common.entity.diary.Diary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 请求体：前端一次性提交 diary 与 blocks
 * - diary.id 为空 -> create
 * - diary.id 非空  -> update (需要传 version 做乐观锁)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "保存日记请求：包含 diary 元信息与 blocks 列表")
public class DiarySaveDTO {
    @Schema(description = "日记基本信息（title, id, version 等）", implementation = Diary.class)
    private Diary diary;

    @Schema(description = "该日记的块列表（顺序将决定 position）", implementation = BlockDTO.class)
    private List<BlockDTO> blocks;
}