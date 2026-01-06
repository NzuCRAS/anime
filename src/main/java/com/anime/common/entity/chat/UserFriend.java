package com.anime.common.entity.chat;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_friends")
public class UserFriend {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 当前用户ID（users.id）
     */
    private Long userId;

    /**
     * 好友用户ID（users.id）
     */
    private Long friendId;

    /**
     * 建立好友关系时间
     */
    @TableField(value = "created_at",fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}