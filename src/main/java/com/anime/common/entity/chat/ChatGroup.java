package com.anime.common.entity.chat;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_groups")
public class ChatGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 群名称
     */
    private String name;

    /**
     * 群主用户ID（users.id）
     */
    private Long ownerId;

    /**
     * 群简介（可为空）
     */
    private String description;

    @TableField(value = "created_at",fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at",fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}