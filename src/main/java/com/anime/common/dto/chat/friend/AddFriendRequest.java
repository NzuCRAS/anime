package com.anime.common.dto.chat.friend;

import lombok.Data;

/**
 * 添加好友请求
 */
@Data
public class AddFriendRequest {

    /**
     * 好友的 UUID（当前实现中等价于对方的 userId）
     */
    private Long friendUid;
}