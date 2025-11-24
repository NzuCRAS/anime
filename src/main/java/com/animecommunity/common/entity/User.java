package com.animecommunity.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类 - MyBatis-Plus 版本
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")  // 指定表名
public class User {

    /**
     * 用户UUID主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)  // UUID 主键
    private String id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 加密密码
     */
    private String password;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像图片路径
     */
    @TableField("avatar_path")  // 字段映射
    private String avatarPath;

    /**
     * 创建时间
     */
    @TableField(value = "created_time", fill = FieldFill.INSERT)  // 插入时自动填充
    private LocalDateTime createdTime;

    /**
     * 上次登录时间
     */
    @TableField("last_login")
    private LocalDateTime lastLogin;

    /**
     * 个性签名
     */
    @TableField("personal_signature")
    private String personalSignature;

    // 便捷构造方法
    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }
}