package com.anime.common.mapper.chat;

import com.anime.common.entity.chat.UserFriendRequest;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

/**
 * Mapper for friend_requests table
 */
@Mapper
public interface UserFriendRequestMapper extends BaseMapper<UserFriendRequest> {

    @Select("SELECT * FROM friend_requests WHERE to_user_id = #{toUserId} AND status = 'pending' ORDER BY created_at DESC")
    java.util.List<UserFriendRequest> listPendingForUser(@Param("toUserId") Long toUserId);

    @Select("SELECT * FROM friend_requests WHERE id = #{id} LIMIT 1")
    UserFriendRequest findById(@Param("id") Long id);

    @Update("UPDATE friend_requests SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateStatusById(@Param("id") Long id, @Param("status") String status);

    @Select("SELECT * FROM friend_requests WHERE from_user_id = #{fromUserId} AND to_user_id = #{toUserId} LIMIT 1")
    UserFriendRequest findByFromTo(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);
}