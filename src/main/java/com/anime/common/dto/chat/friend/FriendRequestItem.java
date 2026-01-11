package com.anime.common.dto.chat.friend;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendRequestItem {
    private Long requestId;
    private Long fromUserId;
    private String fromUsername;
    private String fromAvatarUrl;
    private String message;
    private String signature;
    private LocalDateTime createdAt;
}