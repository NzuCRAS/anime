package com.anime.diary.controller;

import com.anime.auth.web.CurrentUser;
import com.anime.common.dto.diary.DiarySaveDTO;
import com.anime.common.entity.diary.Diary;
import com.anime.diary.service.DiaryService;
import com.anime.diary.service.DiaryService.SaveResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * DiaryController - 提供查询与保存（新建/更新）日记的接口。
 *
 * - GET  /api/diaries/{id}     : 获取日记及其 blocks
 * - POST /api/diaries          : 新建或更新日记（传 diary + blocks）
 *
 * 说明：
 * - 使用 @CurrentUser 注入当前用户 id（由 JwtAuthenticationFilter 设置 Authentication.principal）
 * - 在此示例中没有做复杂的权限判断（例如检查 diary.userId == currentUserId），建议在生产加上此检查
 */
@Slf4j
@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    /**
     * 传入diaryId,返回该diaryId对应的日记里的所有blocks
     * @param diaryId
     * @param currentUserId
     * @return
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDiaryWithBlocks(@PathVariable("id") Long diaryId,
                                                @CurrentUser Long currentUserId) {
        if (currentUserId == null) {
            return ResponseEntity.status(401).body("unauthorized");
        }
        SaveResult result = diaryService.getDiaryWithBlocks(diaryId);
        if (result == null || result.getDiary() == null) {
            return ResponseEntity.notFound().build();
        }
        // OPTIONAL: 权限校验，确保 currentUserId == result.getDiary().getUserId()
        return ResponseEntity.ok(result);
    }

    /**
     * 传入DiarySaveRequestDTO,返回
     * @param req
     * @param currentUserId
     * @return
     */
    @PostMapping
    public ResponseEntity<?> saveDiary(@RequestBody DiarySaveDTO req,
                                       @CurrentUser Long currentUserId) {
        if (currentUserId == null) {
            return ResponseEntity.status(401).body("unauthorized");
        }
        if (req == null || req.getDiary() == null) {
            return ResponseEntity.badRequest().body("diary required");
        }

        // Ensure diary.userId is set to current user (security)
        Diary d = req.getDiary();
        d.setUserId(currentUserId);

        try {
            SaveResult sr = diaryService.saveDiaryWithBlocks(d, req.getBlocks(), currentUserId);
            return ResponseEntity.ok(sr);
        } catch (IllegalStateException ex) {
            // 版本冲突或不存在
            return ResponseEntity.status(409).body(ex.getMessage());
        } catch (Exception ex) {
            log.error("saveDiary failed", ex);
            return ResponseEntity.status(500).body("save failed: " + ex.getMessage());
        }
    }
}