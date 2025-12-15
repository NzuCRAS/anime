package com.anime.diary.service;

import com.anime.common.entity.attachment.Attachment;
import com.anime.common.entity.diary.Block;
import com.anime.common.entity.diary.Diary;
import com.anime.common.dto.diary.BlockDTO;
import com.anime.common.mapper.attachment.AttachmentMapper;
import com.anime.common.mapper.diary.DiaryMapper;
import com.anime.diary.service.BlockService;
import com.anime.common.service.AttachmentService;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DiaryService - 负责 diary 的保存/查询及与 blocks/attachment 的联动。
 *
 * 设计说明：
 * - 采用乐观锁 version 字段：更新时以 WHERE id=? AND version=? 来保证并发写入检测。
 * - 保存流程（saveDiaryWithBlocks）：
 *   1) 若 diary.id == null -> 新建 diary 并写入 createdAt/updatedAt/version=1
 *   2) 否则按 version 做乐观更新（更新 title/updatedAt/version=version+1）
 *   3) 调用 BlockService.saveBlocksForDiary 保存该 diary 的 blocks（批量优化）
 *   4) 收集 blocks 中引用到的 attachmentId，逐个调用 AttachmentService.completeUpload(...) 去 headObject 并把对应 attachment 标记为 available
 *      - 这样采用“最终提交时批量 finalize attachments”策略（替代此前每次上传都做 complete 的做法）。
 * - 返回值 SaveResult 包含最终的 Diary 与该 Diary 的最新 Blocks 列表（从 DB 查询）。
 *
 * 注意：
 * - completeUpload 会进行网络调用（headObject），因此该方法可能较慢；如果你想降低提交延迟，可以把 completeUpload 的部分改为异步任务队列。
 * - operatorId 用于权限校验和审计（当前实现仅做日志记录，未实现复杂权限判断）。在实际场景请验证 operatorId 是否有权限修改该 diary。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryMapper diaryMapper;
    private final BlockService blockService;
    private final AttachmentService attachmentService;

    /**
     * 保存日记（新建或更新）并保存/替换其 blocks。
     *
     * @param diaryInput 前端传入的 diary（若 id 为 null 则创建）
     * @param blocks     前端传入的 blocks 列表（顺序代表最终 position）
     * @param operatorId 操作用户 id（用于权限校验 / 审计）
     * @return 保存后的结果，包含 diary 与 blocks
     */
    @Transactional
    public SaveResult saveDiaryWithBlocks(Diary diaryInput, List<BlockDTO> blocks, Long operatorId) {
        if (diaryInput == null) throw new IllegalArgumentException("diaryInput required");
        if (blocks == null) blocks = Collections.emptyList();

        Long diaryId;
        LocalDateTime now = LocalDateTime.now();

        // 1) 新建或更新 diary（乐观锁）
        if (diaryInput.getId() == null) {
            // create
            diaryInput.setCreatedAt(now);
            diaryInput.setUpdatedAt(now);
            diaryInput.setVersion(1L);
            diaryMapper.insert(diaryInput);
            diaryId = diaryInput.getId();
            log.info("Created diary id={} by user={}", diaryId, operatorId);
        } else {
            diaryId = diaryInput.getId();
            // optimistic update: set title/updatedAt and bump version where version matches
            Long expectedVersion = diaryInput.getVersion() == null ? 0L : diaryInput.getVersion();
            UpdateWrapper<Diary> uw = new UpdateWrapper<>();
            uw.eq("id", diaryId)
                    .eq("version", expectedVersion)
                    .set("title", diaryInput.getTitle())
                    .set("updated_at", now)
                    .set("version", expectedVersion + 1);
            int updated = diaryMapper.update(null, uw);
            if (updated == 0) {
                // 版本冲突或不存在
                throw new IllegalStateException("Diary update failed due to version conflict or not found (id=" + diaryId + ")");
            }
            log.info("Updated diary id={} by user={} (version {} -> {})", diaryId, operatorId, expectedVersion, expectedVersion + 1);
        }

        // 2) 保存 blocks（BlockService 内部会按前端顺序重写 position、批量 insert/update、软删除未包含的）
        List<Block> savedBlocks = blockService.saveBlocksForDiary(diaryId, blocks);

        // 3) 收集 blocks 中被使用的 attachmentId，并 finalize（completeUpload）这些 attachment
        //    采用最终提交时批量 finalize 的策略：只有在业务确认后才将 attachment 状态改为 available
        Set<Long> chosenAttachmentIds = blocks.stream()
                .map(BlockDTO::getAttachmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!chosenAttachmentIds.isEmpty()) {
            log.info("Finalizing {} attachments for diary {} by user {}", chosenAttachmentIds.size(), diaryId, operatorId);
            for (Long aid : chosenAttachmentIds) {
                try {
                    // completeUpload 会 headObject 并把 attachment 标记为 available / 更新 checksum/size/url 等
                    Attachment att = attachmentService.completeUpload(aid);
                    log.debug("Attachment {} finalized for diary {}: {}", aid, diaryId, att == null ? "null" : att.getStatus());
                } catch (Exception ex) {
                    // 不要中断主流程：如果某个 attachment finalize 失败，记录日志并继续处理其它 attachment
                    log.warn("Failed to finalize attachment id={} for diary {}: {}", aid, diaryId, ex.getMessage());
                }
            }
        }

        // 4) 返回最终的 diary（从 DB 读取以包含最新 version）和 blocks（已经是最新）
        Diary finalDiary = diaryMapper.selectById(diaryId);
        List<Block> finalBlocks = blockService.getBlocksByDiaryId(diaryId);

        return new SaveResult(finalDiary, finalBlocks);
    }

    /**
     * 简单读取 diary（不包含 blocks）
     */
    public Diary getDiary(Long diaryId) {
        if (diaryId == null) return null;
        return diaryMapper.selectById(diaryId);
    }

    /**
     * 读取 diary 与其 blocks（按 position）
     */
    public SaveResult getDiaryWithBlocks(Long diaryId) {
        Diary d = getDiary(diaryId);
        if (d == null) return null;
        List<Block> blocks = blockService.getBlocksByDiaryId(diaryId);
        return new SaveResult(d, blocks);
    }

    @Data
    @AllArgsConstructor
    public static class SaveResult {
        private Diary diary;
        private List<Block> blocks;
    }
}