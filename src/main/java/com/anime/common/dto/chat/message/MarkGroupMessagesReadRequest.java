package com.anime.common.dto.chat.message;

import lombok.Data;

@Data
public class MarkGroupMessagesReadRequest {
    private Long groupId;
}