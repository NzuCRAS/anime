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

    /**
     * 查找 A 与 B 之间是否存在“未被拒绝”的请求（任一方向）。
     * 返回任意一条（如果存在）。用于阻止重复申请：
     * - 如果存在 status != 'rejected'，则视为不可再次申请。
     */
    @Select("""
        SELECT *
        FROM friend_requests
        WHERE ((from_user_id = #{a} AND to_user_id = #{b})
            OR  (from_user_id = #{b} AND to_user_id = #{a}))
          AND status != 'rejected'
        LIMIT 1
        """)
    UserFriendRequest findActiveBetween(@Param("a") Long a, @Param("b") Long b);

    /**
     * 原子化更新：仅当当前状态等于 expectedCurrentStatus 时，才把 status 更新为 newStatus。
     * 返回受影响行数（0 or 1）。用于避免并发下的状态覆盖。
     */
    @Update("""
        UPDATE friend_requests
        SET status = #{newStatus}, updated_at = CURRENT_TIMESTAMP
        WHERE id = #{id} AND status = #{expectedCurrentStatus}
        """)
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("newStatus") String newStatus,
                              @Param("expectedCurrentStatus") String expectedCurrentStatus);
}