package com.anime.common.dto.diary;

import com.anime.common.entity.diary.Diary;
import lombok.Data;

import java.util.List;

/**
 * 请求体：前端一次性提交 diary 与 blocks
 * - diary.id 为空 -> create
 * - diary.id 非空  -> update (需要传 version 做乐观锁)
 */
@Data
public class DiarySaveDTO {
    private Diary diary;
    private List<BlockDTO> blocks;
}