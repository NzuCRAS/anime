package com.anime.chat.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.chat.service.ChatMessageService;
import com.anime.common.dto.chat.message.*;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 历史消息查询接口：
 * - 获取与某好友的历史私聊消息
 * - 获取某个群的历史群聊消息
 *
 * 实时发送/接收走 WebSocket，不通过这里。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    /**
     * 获取与某好友的历史私聊消息
     */
    @PostMapping("/private")
    public Result<ListPrivateMessagesResponse> listPrivateMessages(
            @RequestBody ListPrivateMessagesRequest request,
            @CurrentUser Long userId) {

        try {
            ListPrivateMessagesResponse resp = chatMessageService.listPrivateMessages(request, userId);
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }

    /**
     * 获取某个群的历史群聊消息
     */
    @PostMapping("/group")
    public Result<ListGroupMessagesResponse> listGroupMessages(
            @RequestBody ListGroupMessagesRequest request,
            @CurrentUser Long userId) {

        try {
            ListGroupMessagesResponse resp = chatMessageService.listGroupMessages(request, userId);
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }

    /**
     * 将与某好友的私聊消息标记为已读（当前用户作为接收方）。
     */
    @PostMapping("/private/markRead")
    public Result<MarkPrivateMessagesReadResponse> markPrivateMessagesRead(
            @RequestBody MarkPrivateMessagesReadRequest request,
            @CurrentUser Long userId) {
        try {
            MarkPrivateMessagesReadResponse resp = chatMessageService.markPrivateMessagesRead(request, userId);
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage());
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            log.error(e.getMessage());
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }

    /**
     * 将某个群聊的消息全部标记为已读（当前用户作为接收方）。
     */
    @PostMapping("/group/markRead")
    public Result<MarkGroupMessagesReadResponse> markGroupMessagesRead(
            @RequestBody MarkGroupMessagesReadRequest request,
            @CurrentUser Long userId) {
        try {
            MarkGroupMessagesReadResponse resp = chatMessageService.markGroupMessagesRead(request, userId);
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage());
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }

    /**
     * 单向删除消息（仅对自己隐藏该条消息）。
     */
    @PostMapping("/delete")
    public Result<DeleteMessageResponse> deleteMessageForMe(
            @RequestBody DeleteMessageRequest request,
            @CurrentUser Long userId) {

        try {
            DeleteMessageResponse resp = chatMessageService.deleteMessageForUser(request, userId);
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            log.error("deleteMessageForMe param error: {}", e.getMessage());
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            log.error("deleteMessageForMe system error", e);
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }
}