package com.anime.common.mapper;

import com.anime.common.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户数据访问层 - MyBatis-Plus 版本
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    // 基础 CRUD 都不需要写，MyBatis-Plus 自动提供

    // 只需要写特殊的查询（如果有的话）
    @Select("SELECT * FROM users WHERE username = #{username} OR email = #{email}")
    User findByUsernameOrEmail(String username, String email);

    @Update("UPDATE users SET last_login = NOW() WHERE id = #{userId}")
    int updateLastLogin(String userId);
}