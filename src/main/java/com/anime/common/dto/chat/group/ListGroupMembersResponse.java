package com.anime.common.dto.chat.group;

import lombok.Data;

import java.util.List;

/**
 * 获取群成员列表响应
 */
@Data
public class ListGroupMembersResponse {

    /**
     * 群内所有成员
     */
    private List<GroupMember> members;
}