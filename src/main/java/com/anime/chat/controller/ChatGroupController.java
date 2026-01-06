package com.anime.chat.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.chat.service.ChatGroupService;
import com.anime.common.dto.chat.group.*;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 群聊管理接口：
 * - 创建群聊
 * - 获取当前用户所在的群聊列表
 * - 获取群成员列表
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/groups")
@RequiredArgsConstructor
public class ChatGroupController {

    private final ChatGroupService chatGroupService;

    /**
     * 创建群聊
     *
     * 当前用户作为群主，memberUuids 为需要拉入群的成员列表（不含自己）
     */
    @PostMapping("/create")
    public Result<CreateGroupResponse> createGroup(
            @RequestBody CreateGroupRequest request,
            @CurrentUser Long userId) {

        try {
            CreateGroupResponse resp = chatGroupService.createGroup(request, userId);
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            log.error("Create Group failed, userId={}, request={}", userId, request, e);
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            log.error("Create Group failed, userId={}, request={}", userId, request, e);
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }

    /**
     * 获取当前用户所在的群聊列表
     */
    @PostMapping("/list")
    public Result<ListGroupsResponse> listMyGroups(
            @RequestBody(required = false) ListGroupsRequest request,
            @CurrentUser Long userId) {

        if (request == null) {
            request = new ListGroupsRequest();
        }
        ListGroupsResponse resp = chatGroupService.listMyGroups(request, userId);
        return Result.success(resp);
    }

    /**
     * 获取指定群的成员列表
     */
    @PostMapping("/members")
    public Result<ListGroupMembersResponse> listGroupMembers(
            @RequestBody ListGroupMembersRequest request,
            @CurrentUser Long userId) {

        try {
            ListGroupMembersResponse resp = chatGroupService.listGroupMembers(request, userId);
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }
}