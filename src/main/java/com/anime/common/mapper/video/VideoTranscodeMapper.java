package com.anime.common.mapper.video;

import com.anime.common.entity.video.VideoTranscode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VideoTranscodeMapper extends BaseMapper<VideoTranscode> {

    @Select("""
        SELECT * FROM video_transcodes
        WHERE video_id = #{videoId}
          AND status = 'ready'
        ORDER BY bitrate ASC
        """)
    List<VideoTranscode> listReadyByVideoId(Long videoId);

    @Select("SELECT * FROM video_transcodes WHERE video_id = #{videoId} ORDER BY bitrate DESC")
    List<VideoTranscode> listByVideoId(Long videoId);
}