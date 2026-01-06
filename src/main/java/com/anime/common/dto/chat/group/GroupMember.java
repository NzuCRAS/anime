package com.anime.common.dto.chat.group;

import lombok.Data;

/**
 * 群成员信息
 */
@Data
public class GroupMember {

    /**
     * 成员用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 成员头像 URL
     */
    private String avatarUrl;

    /**
     * 在群中的角色：member / admin / owner
     */
    private String role;
}