package com.anime.common.mapper.user;

import com.anime.common.entity.user.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户数据访问层 - MyBatis-Plus 版本
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    // 使用 @Param 明确绑定参数名，避免 -parameters 未启用导致的绑定问题
    @Select("SELECT * FROM users WHERE username = #{username} OR email = #{email}")
    User findByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

    @Select("SELECT * FROM users WHERE id = #{userId}")
    User findById(@Param("userId") Long userId);

    // 统一使用 Long 类型作为 userId
    @Update("UPDATE users SET last_login = NOW() WHERE id = #{userId}")
    int updateLastLogin(@Param("userId") Long userId);

    @Select("SELECT username FROM users WHERE id = #{userId}")
    String getUserNameById(@Param("userId") Long userId);

    @Select("SELECT avatar_attachment_id FROM users WHERE id = #{userId}")
    Long getAvatarAttachmentIdById(@Param("userId") Long userId);

    @Select("SELECT personal_signature FROM users WHERE id = #{userId}")
    String getPersonalSignatureById(@Param("userId") Long userId);
}