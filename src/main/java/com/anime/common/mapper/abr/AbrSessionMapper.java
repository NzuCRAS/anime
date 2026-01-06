package com.anime.common.mapper.abr;

import com.anime.common.entity.abr.AbrSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AbrSessionMapper extends BaseMapper<AbrSession> {

    @Select("SELECT * FROM abr_sessions WHERE session_uuid = #{sessionUuid} LIMIT 1")
    AbrSession findBySessionUuid(String sessionUuid);
}