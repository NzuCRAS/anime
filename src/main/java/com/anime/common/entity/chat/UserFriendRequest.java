package com.anime.common.entity.chat;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * friend_requests 表对应的实体
 */
@Data
@TableName("friend_requests")
public class UserFriendRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 请求发起方 user id
     */
    private Long fromUserId;

    /**
     * 请求接收方 user id
     */
    private Long toUserId;

    /**
     * 附带的请求消息（可为空）
     */
    private String message;

    /**
     * 请求状态：pending / accepted / rejected
     */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}