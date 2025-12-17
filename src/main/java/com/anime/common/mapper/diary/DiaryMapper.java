package com.anime.common.mapper.diary;

import com.anime.common.entity.diary.Diary;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * DiaryMapper
 * 用于 diaries 表的基本操作。建议在保存/更新逻辑中使用乐观锁（version）进行冲突检测。
 */
@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {
    // 若需要原子性地做 version 检查并更新，可额外添加方法或直接使用 SQL：
    // int updateVersionIfMatch(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

    /**
     * 通过用户id和日期(LocalDate类型)查询相应的日记
     * @param userId
     * @param date
     * @return
     */
    @Select("SELECT * FROM diaries WHERE user_id = #{userId} AND DATE(created_at) = #{date} AND deleted_at IS NULL")
    List<Diary> selectIdsByUserIdAndCreatedDate(@Param("userId") Long userId, @Param("date") LocalDate date);


}