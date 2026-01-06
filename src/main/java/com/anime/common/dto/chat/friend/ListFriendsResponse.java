package com.anime.common.dto.chat.friend;

import lombok.Data;

import java.util.List;

/**
 * 获取好友列表响应
 */
@Data
public class ListFriendsResponse {

    /**
     * 当前用户的所有好友
     */
    private List<FriendItem> friends;
}