package com.anime.common.dto.chat.friend;

import lombok.Data;

import java.util.List;

@Data
public class SearchUserResponse {
    private List<FriendSearchItem> items;
}