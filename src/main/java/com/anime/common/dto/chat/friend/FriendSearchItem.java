package com.anime.common.dto.chat.friend;

import lombok.Data;

@Data
public class FriendSearchItem {
    private Long id;
    private String username;
    private String avatarUrl;
    private String personalSignature;
}