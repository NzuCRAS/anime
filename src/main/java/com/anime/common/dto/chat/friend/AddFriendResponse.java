package com.anime.common.dto.chat.friend;

import lombok.Data;

/**
 * 添加好友响应：返回新好友的基本信息
 */
@Data
public class AddFriendResponse {

    /**
     * 好友用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 好友头像 URL
     */
    private String avatarUrl;
}