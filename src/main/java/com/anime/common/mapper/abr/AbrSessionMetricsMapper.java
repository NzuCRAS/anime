package com.anime.common.mapper.abr;

import com.anime.common.entity.abr.AbrSessionMetrics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AbrSessionMetricsMapper extends BaseMapper<AbrSessionMetrics> {

    @Select("SELECT * FROM abr_session_metrics WHERE session_id = #{sessionId} LIMIT 1")
    AbrSessionMetrics findBySessionId(Long sessionId);
}