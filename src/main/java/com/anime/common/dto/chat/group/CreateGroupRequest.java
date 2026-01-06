package com.anime.common.dto.chat.group;

import lombok.Data;

import java.util.List;

/**
 * 创建群聊请求
 */
@Data
public class CreateGroupRequest {

    /**
     * 群名称
     */
    private String name;

    /**
     * 群简介（可选）
     */
    private String description;

    /**
     * 要邀请加入群聊的成员 UUID 列表
     * 当前实现中等价于 userId 列表（不需要包含群主自己）
     */
    private List<Long> memberUuids;
}