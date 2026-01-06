package com.anime.common.dto.chat.friend;

import lombok.Data;

/**
 * 好友列表中单个好友信息
 */
@Data
public class FriendItem {

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