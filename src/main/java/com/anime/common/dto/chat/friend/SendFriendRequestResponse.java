package com.anime.common.dto.chat.friend;

import lombok.Data;

@Data
public class SendFriendRequestResponse {
    private Long requestId;
    private String status; // "pending"
}