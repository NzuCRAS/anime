package com.anime.common.mapper.video;

import com.anime.common.entity.video.VideoLike;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * VideoLike mapper (adjusted for active flag)
 */
@Mapper
public interface VideoLikeMapper extends BaseMapper<VideoLike> {

    @Select("SELECT COUNT(1) FROM video_likes WHERE video_id = #{videoId} AND active = 1")
    Long countActiveByVideoId(Long videoId);

    @Select("SELECT id, active FROM video_likes WHERE video_id = #{videoId} AND user_id = #{userId} LIMIT 1")
    VideoLike selectByVideoAndUser(@Param("videoId") Long videoId, @Param("userId") Long userId);

    @Update("UPDATE video_likes SET active = #{active} WHERE id = #{id}")
    int updateActiveById(@Param("id") Long id, @Param("active") int active);

    @Insert("INSERT INTO video_likes (video_id, user_id, active, created_at) VALUES (#{videoId}, #{userId}, #{active}, NOW())")
    int insertLike(@Param("videoId") Long videoId, @Param("userId") Long userId, @Param("active") int active);
}