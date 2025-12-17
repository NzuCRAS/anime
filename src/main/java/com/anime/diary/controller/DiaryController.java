package com.anime.diary.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.common.dto.attachment.PresignRequestDTO;
import com.anime.common.dto.attachment.PresignResponseDTO;
import com.anime.common.dto.diary.DiarySaveDTO;
import com.anime.common.dto.diary.GetUserDiaryDTO;
import com.anime.common.entity.diary.Diary;
import com.anime.common.enums.ResultCode;
import com.anime.common.service.AttachmentService;
import com.anime.diary.service.DiaryService;
import com.anime.diary.service.DiaryService.SaveResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * DiaryController - 提供查询与保存（新建/更新）日记的接口。
 */
@Tag(name = "Diary", description = "日记相关接口（保存/查询/删除/上传）")
@Slf4j
@RestController
@RequestMapping("/api/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;
    private final AttachmentService attachmentService;

    @Operation(summary = "获取 presign（Diary 专用）", description = "生成 presigned PUT URL，供前端上传 diary 相关附件")
    @PostMapping("/presign")
    public ResponseEntity<?> presign(@RequestBody PresignRequestDTO req, @CurrentUser Long userId) {
        String storagePath = "/diary/" + userId + "/"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
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

    @Operation(summary = "按 diaryId 获取日记及其 blocks", description = "返回 diary 与按 position 排序的 blocks（image 类型会包含 attachmentUrl）")
    @GetMapping("/id/{id}")
    public ResponseEntity<?> getDiaryWithBlocks(@PathVariable("id") Long diaryId) {
        SaveResult result = diaryService.getDiaryWithBlocks(diaryId);
        if (result == null || result.getDiary() == null) {
            return ResponseEntity.status(ResultCode.NOT_FOUND.getCode()).body("getDiaryWithBlocks failed");
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "保存日记（新建或更新）", description = "传入 diary + blocks；若 diary.id 为空则创建，否则按 version 做乐观锁更新")
    @PostMapping("/save")
    public ResponseEntity<?> saveDiary(@RequestBody DiarySaveDTO req,
                                       @CurrentUser Long userId) {
        if (req == null || req.getDiary() == null) {
            return ResponseEntity.status(ResultCode.BAD_REQUEST.getCode()).body("diary required");
        }
        Diary d = req.getDiary();
        d.setUserId(userId);
        try {
            SaveResult sr = diaryService.saveDiaryWithBlocks(d, req.getBlocks(), userId);
            return ResponseEntity.ok(sr);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(ResultCode.VERSION_CONFLICT.getCode()).body(ex.getMessage());
        } catch (Exception ex) {
            log.error("saveDiary failed", ex);
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body("save failed: " + ex.getMessage());
        }
    }

    @Operation(summary = "获取当前用户的所有日记摘要", description = "仅返回 id/title/createdAt")
    @GetMapping("/getMyDiary")
    public ResponseEntity<?> listMyDiaries(@CurrentUser Long currentUserId) {
        List<GetUserDiaryDTO> list = diaryService.getDiariesByUserId(currentUserId);
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "按日期获取当前用户的日记摘要", description = "参数为 yyyy-MM-dd 格式的日期")
    @GetMapping("/date/{date}")
    public ResponseEntity<?> listMyDateDiaries(@CurrentUser Long currentUserId, @PathVariable("date") LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            return ResponseEntity.status(ResultCode.BAD_REQUEST.getCode()).body("date is invalid");
        }
        List<GetUserDiaryDTO>  list = diaryService.getDiariesByUserIdAndDate(currentUserId, date);
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "删除日记（软删除）", description = "仅日记拥有者可删除")
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteDiary(@PathVariable("id") Long diaryId,
                                         @CurrentUser Long currentUserId) {
        if (currentUserId == null) {
            return ResponseEntity.status(ResultCode.UNAUTHORIZED.getCode()).body("unauthorized");
        }
        try {
            diaryService.deleteDiary(diaryId, currentUserId);
            return ResponseEntity.ok().body("deleted");
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(ResultCode.NOT_FOUND.getCode()).body(ex.getMessage());
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return ResponseEntity.status(ResultCode.FORBIDDEN.getCode()).body("forbidden");
        } catch (Exception ex) {
            log.error("deleteDiary failed", ex);
            return ResponseEntity.status(ResultCode.SYSTEM_ERROR.getCode()).body("delete failed: " + ex.getMessage());
        }
    }
}