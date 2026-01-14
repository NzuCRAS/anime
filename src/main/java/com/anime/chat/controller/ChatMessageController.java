package com.anime.chat.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.chat.service.ChatMessageService;
import com.anime.common.dto.attachment.PresignRequestDTO;
import com.anime.common.dto.attachment.PresignResponseDTO;
import com.anime.common.dto.chat.message.*;
import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import com.anime.common.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 历史消息查询与消息操作接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final AttachmentService attachmentService;

    @Operation(summary = "获取 presign（聊天上传文件）", description = "生成 presigned PUT URL，供前端上传用户文件")
    @PostMapping("/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequestDTO req, @CurrentUser Long userId) {
        String storagePath = "/chatFile/" + userId + "/"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        log.info("presign request storagePath={} originalFilename={} mimeType={} uploadedBy={}",
                storagePath, req.getOriginalFilename(), req.getMimeType(), userId);
        try {
            PresignResponseDTO resp = attachmentService.preCreateAndPresign(
                    storagePath, req.getMimeType(), userId, req.getOriginalFilename(), null, null);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            log.error("presign failed", ex);
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body("presign failed");
        }
    }

    @PostMapping("/private/getMessage")
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

    @PostMapping("/group/getMessage")
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

    @PostMapping("/private/markRead")
    public Result<MarkPrivateMessagesReadResponse> markPrivateMessagesRead(
            @RequestBody MarkPrivateMessagesReadRequest request,
            @CurrentUser Long userId) {
        try {
            System.out.println("======================== read =========================");
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
     * 单向删除（仅删除当前用户视角该条消息）
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

    /**
     * 撤回消息（仅发送者在3分钟内可撤回，删除所有人的该条逻辑消息）
     */
    @PostMapping("/recall")
    public Result<RecallMessageResponse> recallMessage(
            @RequestBody RecallMessageRequest request,
            @CurrentUser Long userId) {
        try {
            RecallMessageResponse resp = chatMessageService.recallMessage(request, userId);
            if (!resp.isAllowed()) {
                return Result.fail(ResultCode.PARAM_ERROR, resp);
            }
            return Result.success(resp);
        } catch (IllegalArgumentException e) {
            log.warn("recallMessage validation failed userId={} req={} err={}", userId, request, e.getMessage());
            return Result.fail(ResultCode.PARAM_ERROR, null);
        } catch (Exception e) {
            log.error("recallMessage system error", e);
            return Result.fail(ResultCode.SYSTEM_ERROR, null);
        }
    }
}