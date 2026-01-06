package com.anime.common.dto.chat.group;


import lombok.Data;

/**
 * 获取群成员列表请求
 */
@Data
public class ListGroupMembersRequest {

    /**
     * 群ID
     */
    private Long groupId;
}