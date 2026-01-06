package com.anime.common.dto.chat.group;

import lombok.Data;

/**
 * 群列表中的单个群条目
 */
@Data
public class GroupItem {

    /**
     * 群ID
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
