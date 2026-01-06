package com.anime.common.dto.chat.friend;

import lombok.Data;

import java.util.List;

@Data
public class ListFriendRequestsResponse {
    private List<FriendRequestItem> items;
}