package com.anime.chat.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.chat.service.ChatSessionService;
import com.anime.common.dto.chat.session.ListSessionsRequest;
import com.anime.common.dto.chat.session.ListSessionsResponse;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 会话列表接口：
 * - 返回当前用户的所有会话（单聊 + 群聊），
 *   供聊天页面左侧列表展示。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /**
     * 获取当前用户所有会话列表
     *
     * 按最后一条消息时间从新到旧排序，
     * 包括：
     * - 与每个好友的最近一次对话
     * - 每个所在群的最近一条消息
     */
    @PostMapping("/list")
    public Result<ListSessionsResponse> listSessions(
            @CurrentUser Long userId) {
        try {
            ListSessionsResponse resp = chatSessionService.listSessions(userId);
            log.error("===========================session list==============================");
            log.error(resp.toString());
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage());
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            log.error(e.getMessage());
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }
}