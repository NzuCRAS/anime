package com.anime.common.mapper.chat;

import com.anime.common.entity.chat.UserFriend;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFriendMapper extends BaseMapper<UserFriend> {
    // 基础 CRUD 由 MyBatis-Plus 提供
}