package com.anime.common.dto.chat.group;

import lombok.Data;

import java.util.List;

/**
 * 获取当前用户所在群聊列表响应
 */
@Data
public class ListGroupsResponse {

    /**
     * 当前用户所在的所有群聊
     */
    private List<GroupItem> groups;
}
