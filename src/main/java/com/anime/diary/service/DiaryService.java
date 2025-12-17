package com.anime.diary.service;

import com.anime.common.dto.diary.GetUserDiaryDTO;
import com.anime.common.entity.attachment.Attachment;
import com.anime.common.entity.diary.Block;
import com.anime.common.entity.diary.Diary;
import com.anime.common.dto.diary.BlockDTO;
import com.anime.common.mapper.diary.DiaryMapper;
import com.anime.common.service.AttachmentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DiaryService - 负责 diary 的保存/查询及与 blocks/attachment 的联动。
 *
 * 修改点：
 * - 在返回 diary + blocks 时（getDiaryWithBlocks），对于 type="image" 且 attachmentId 非空的 block，
 *   使用 AttachmentService 生成短期的下载 URL 并设置到 Block.attachmentUrl 字段，前端可直接使用该 URL 显示图片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryMapper diaryMapper;
    private final BlockService blockService;
    private final AttachmentService attachmentService;

    private static final long ATTACHMENT_URL_TTL_SECONDS = 600L; // 10 minutes

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
        Set<Long> chosenAttachmentIds = blocks.stream()
                .map(BlockDTO::getAttachmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!chosenAttachmentIds.isEmpty()) {
            log.info("Finalizing {} attachments for diary {} by user {}", chosenAttachmentIds.size(), diaryId, operatorId);
            for (Long aid : chosenAttachmentIds) {
                try {
                    Attachment att = attachmentService.completeUpload(aid);
                    log.debug("Attachment {} finalized for diary {}: {}", aid, diaryId, att == null ? "null" : att.getStatus());
                } catch (Exception ex) {
                    log.warn("Failed to finalize attachment id={} for diary {}: {}", aid, diaryId, ex.getMessage());
                }
            }
        }

        // 4) 返回最终的 diary（从 DB 读取以包含最新 version）和 blocks（已经是最新）
        Diary finalDiary = diaryMapper.selectById(diaryId);
        List<Block> finalBlocks = blockService.getBlocksByDiaryId(diaryId);

        // 为 image 类型的 block 动态注入短期下载 URL（attachmentUrl）
        for (Block b : finalBlocks) {
            try {
                if (b != null && "image".equalsIgnoreCase(b.getType()) && b.getAttachmentId() != null) {
                    String url = attachmentService.generatePresignedGetUrl(b.getAttachmentId(), ATTACHMENT_URL_TTL_SECONDS);
                    b.setAttachmentUrl(url);
                }
            } catch (Exception ex) {
                // 不要因为单个 attachment 生成失败而中断整体返回，记录警告即可
                log.warn("failed to generate attachment url for block id={} attachmentId={}: {}", b == null ? null : b.getId(), b == null ? null : b.getAttachmentId(), ex.getMessage());
            }
        }

        return new SaveResult(finalDiary, finalBlocks);
    }

    /**
     * 获取某用户的日记摘要（不包含 blocks）
     * 返回 List of GetUserDiaryDTO (id, title, createdAt)
     */
    public List<GetUserDiaryDTO> getDiariesByUserId(Long userId) {
        if (userId == null) return Collections.emptyList();

        QueryWrapper<Diary> qw = new QueryWrapper<>();
        qw.select("id", "title", "created_at")
                .eq("user_id", userId)
                .isNull("deleted_at")
                .orderByDesc("created_at");

        List<Diary> diaries = diaryMapper.selectList(qw);
        if (diaries == null || diaries.isEmpty()) return Collections.emptyList();

        return diaries.stream()
                .map(d -> new GetUserDiaryDTO(d.getId(), d.getTitle(), d.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * 根据用户id和日期获取所有用户的日记摘要
     * @param userId
     * @param date
     * @return
     */
    public List<GetUserDiaryDTO> getDiariesByUserIdAndDate(Long userId, LocalDate date) {
        if (userId == null) return Collections.emptyList();
        if (date == null) return Collections.emptyList();
        List<Diary> diaries = diaryMapper.selectIdsByUserIdAndCreatedDate(userId, date);
        List<GetUserDiaryDTO> getUserDiaryDTOs = new ArrayList<>();
        for (Diary diary : diaries) {
            GetUserDiaryDTO dto = new GetUserDiaryDTO();
            dto.setId(diary.getId());
            dto.setTitle(diary.getTitle());
            dto.setCreatedAt(diary.getCreatedAt());
            getUserDiaryDTOs.add(dto);
        }
        return getUserDiaryDTOs;
    }

    /**
     * 删除（软删除）某个 diary（仅限 diary 的 owner）
     *
     * @param diaryId
     * @param operatorId 当前操作用户 id
     */
    @Transactional
    public void deleteDiary(Long diaryId, Long operatorId) {
        if (diaryId == null) throw new IllegalArgumentException("diaryId required");
        Diary d = diaryMapper.selectById(diaryId);
        if (d == null) throw new IllegalStateException("Diary not found (id=" + diaryId + ")");
        if (!Objects.equals(d.getUserId(), operatorId)) {
            throw new AccessDeniedException("not owner of diary");
        }

        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<Diary> uw = new UpdateWrapper<>();
        uw.eq("id", diaryId).isNull("deleted_at").set("deleted_at", now);
        int updated = diaryMapper.update(null, uw);
        if (updated == 0) {
            throw new IllegalStateException("Diary already deleted or update failed (id=" + diaryId + ")");
        }

        // soft delete blocks
        blockService.softDeleteBlocksForDiary(diaryId);

        log.info("Diary id={} soft-deleted by user={}", diaryId, operatorId);
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
     * 注意：返回的 blocks 中 image 类型的 block 会包含 attachmentUrl 字段（短期 presigned URL 或 CDN URL）
     */
    public SaveResult getDiaryWithBlocks(Long diaryId) {
        Diary d = getDiary(diaryId);
        if (d == null) return null;
        List<Block> blocks = blockService.getBlocksByDiaryId(diaryId);

        for (Block b : blocks) {
            try {
                if (b != null && "image".equalsIgnoreCase(b.getType()) && b.getAttachmentId() != null) {
                    String url = attachmentService.generatePresignedGetUrl(b.getAttachmentId(), ATTACHMENT_URL_TTL_SECONDS);
                    b.setAttachmentUrl(url);
                }
            } catch (Exception ex) {
                log.warn("failed to generate attachment url for block id={} attachmentId={}: {}", b == null ? null : b.getId(), b == null ? null : b.getAttachmentId(), ex.getMessage());
            }
        }

        return new SaveResult(d, blocks);
    }

    @Data
    @AllArgsConstructor
    public static class SaveResult {
        private Diary diary;
        private List<Block> blocks;
    }
}