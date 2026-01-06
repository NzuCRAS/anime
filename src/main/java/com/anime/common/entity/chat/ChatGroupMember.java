package com.anime.common.entity.chat;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_group_members")
public class ChatGroupMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 群ID（chat_groups.id）
     */
    private Long groupId;

    /**
     * 成员用户ID（users.id）
     */
    private Long userId;

    /**
     * 角色：member / admin / owner
     */
    private String role;

    @TableField(value = "joined_at", insertStrategy = FieldStrategy.NEVER)
    private LocalDateTime joinedAt;
}