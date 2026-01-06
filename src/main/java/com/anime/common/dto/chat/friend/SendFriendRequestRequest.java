package com.anime.common.dto.chat.friend;

import lombok.Data;

@Data
public class SendFriendRequestRequest {
    private Long toUserId;
    private String message;
}