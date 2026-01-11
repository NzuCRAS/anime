package com.anime.chat.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.chat.service.FriendService;
import com.anime.common.dto.chat.friend.*;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 好友相关接口：
 * - 添加好友
 * - 获取好友列表
 * - 删除好友
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    /**
     * 获取当前用户的好友列表
     */
    @PostMapping("/list")
    public Result<ListFriendsResponse> listFriends(
            @RequestBody(required = false) ListFriendsRequest request,
            @CurrentUser Long userId) {

        if (request == null) {
            request = new ListFriendsRequest();
        }
        ListFriendsResponse resp = friendService.listFriends(request, userId);
        return Result.success(resp);
    }

    /**
     * 发送好友请求
     */
    @PostMapping("/request/send")
    public Result<SendFriendRequestResponse> sendFriendRequest(@RequestBody SendFriendRequestRequest req,
                                                               @CurrentUser Long userId) {
        req.setFromUserId(userId);
        try {
            SendFriendRequestResponse resp = friendService.sendFriendRequest(req, userId);
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            log.warn("sendFriendRequest failed user={}, req={}: {}", userId, req, e.getMessage());
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            log.error("sendFriendRequest error", e);
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }

    /**
     * 列出收到的好友请求（pending）
     */
    @PostMapping("/request/list")
    public Result<ListFriendRequestsResponse> listFriendRequests(@CurrentUser Long userId) {
        try {
            ListFriendRequestsResponse resp = friendService.listIncomingRequests(userId);
            return Result.success(resp);
        } catch (Exception e) {
            log.error("listFriendRequests error userId={}", userId, e);
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }

    /**
     * 处理好友请求（accept / reject）
     */
    @PostMapping("/request/respond")
    public Result<String> respondFriendRequest(@RequestBody HandleFriendRequestRequest req,
                                               @CurrentUser Long userId) {
        try {
            friendService.handleFriendRequest(req, userId);
            return Result.success("ok");
        } catch (IllegalArgumentException e) {
            log.warn("respondFriendRequest validation failed: {}", e.getMessage());
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            log.error("respondFriendRequest error", e);
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }

    /**
     * 精确id搜索好友（严格匹配） - 返回同时包含与当前用户关系的字段
     */
    @PostMapping("/search")
    public Result<SearchUserResponse> searchUser(@RequestBody SearchUserRequest req,
                                                 @CurrentUser Long userId) {
        try {
            SearchUserResponse resp = friendService.searchById(req, userId);
            return Result.success(resp);
        } catch (Exception e) {
            log.error("searchUser error", e);
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }

    /**
     * 删除好友
     */
    @PostMapping("/remove")
    public Result<Boolean> removeFriend(
            @RequestBody RemoveFriendRequest request,
            @CurrentUser Long userId) {

        try {
            if (friendService.removeFriend(request, userId)){
                return Result.success(true);
            }
            return Result.fail(ResultCode.SYSTEM_ERROR, false);
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }
}