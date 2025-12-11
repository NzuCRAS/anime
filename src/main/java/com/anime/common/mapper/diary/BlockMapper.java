package com.anime.common.mapper.diary;

import com.anime.common.entity.diary.Block;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * BlockMapper
 * 用于 blocks 表的 CRUD 与查询。
 *
 * 常见扩展方法举例（若需要，可实现）：
 * - 查询某篇日记的所有非删除 blocks 并按 position 排序
 * - 批量更新 position（用于重排）
 */
@Mapper
public interface BlockMapper extends BaseMapper<Block> {
    // 示例自定义方法签名（实现时需提供对应 SQL）：
    // List<Block> selectByDiaryIdOrderByPosition(@Param("diaryId") Long diaryId);
}