package com.anime.common.dto.chat.friend;

import com.anime.common.enums.FriendStatus;
import lombok.Data;

@Data
public class FriendSearchItem {
    private Long id;
    private String username;
    private String avatarUrl;
    private String personalSignature;
    private FriendStatus status; // e.g., "ALREADY_FRIENDS", "NOT_FRIENDS", "PENDING_REQUEST"
}