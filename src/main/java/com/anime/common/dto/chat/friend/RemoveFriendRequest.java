package com.anime.common.dto.chat.friend;

import lombok.Data;

/**
 * 删除好友请求
 */
@Data
public class RemoveFriendRequest {

    /**
     * 要删除的好友用户ID
     */
    private Long friendId;
}