package com.anime.common.mapper.video;

import com.anime.common.entity.video.Video;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VideoMapper extends BaseMapper<Video> {

    @Update("UPDATE videos SET like_count = like_count + 1 WHERE id = #{videoId}")
    int incrementLikeCount(Long videoId);

    @Update("UPDATE videos SET like_count = like_count - 1 WHERE id = #{videoId} AND like_count > 0")
    int decrementLikeCount(Long videoId);
}