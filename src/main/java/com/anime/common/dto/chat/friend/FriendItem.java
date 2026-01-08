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
     * 个性签名
     */
    private String personalSignature;

    /**
     * 好友头像 URL
     */
    private String avatarUrl;
}