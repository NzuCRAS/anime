package com.anime.common.dto.chat.friend;

import lombok.Data;

@Data
public class HandleFriendRequestRequest {
    private Long requestId;
    private String action; // "accept" or "reject"
}