package com.anime.common.dto.chat.group;

import lombok.Data;

/**
 * 创建群聊响应
 */
@Data
public class CreateGroupResponse {

    /**
     * 群聊ID
     */
    private Long groupId;

    /**
     * 群名称
     */
    private String name;

    /**
     * 群简介
     */
    private String description;

    /**
     * 群主用户ID
     */
    private Long ownerId;
}