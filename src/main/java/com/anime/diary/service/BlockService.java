package com.anime.diary.service;

import com.anime.common.entity.diary.Block;
import com.anime.common.dto.diary.BlockDTO;
import com.anime.common.mapper.diary.BlockMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BlockService - 批量插入/更新优化版
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockMapper blockMapper;
    private final SqlSessionFactory sqlSessionFactory;

    private static final int BATCH_FLUSH_SIZE = 100;

    public List<Block> getBlocksByDiaryId(Long diaryId) {
        QueryWrapper<Block> qw = new QueryWrapper<>();
        qw.eq("diary_id", diaryId)
                .isNull("deleted_at")
                .orderByAsc("position");
        return blockMapper.selectList(qw);
    }

    @Transactional
    public List<Block> saveBlocksForDiary(Long diaryId, List<BlockDTO> blocks) {
        if (diaryId == null) throw new IllegalArgumentException("diaryId required");
        if (blocks == null) blocks = Collections.emptyList();

        List<Block> existing = getBlocksByDiaryId(diaryId);
        Map<Long, Block> existingById = existing.stream()
                .filter(b -> b.getId() != null)
                .collect(Collectors.toMap(Block::getId, b -> b));

        Set<Long> incomingExistingIds = new HashSet<>();
        List<Block> toInsert = new ArrayList<>();
        List<Block> toUpdate = new ArrayList<>();

        int pos = 1;
        for (BlockDTO dto : blocks) {
            Long incomingId = dto.getBlockId();
            if (incomingId != null && existingById.containsKey(incomingId)) {
                Block db = existingById.get(incomingId);
                db.setType(dto.getType());
                db.setContent(dto.getContent());
                db.setAttachmentId(dto.getAttachmentId());
                db.setMetadata(dto.getMetadata());
                db.setPosition(pos);
                db.setUpdatedAt(LocalDateTime.now());
                toUpdate.add(db);
                incomingExistingIds.add(incomingId);
            } else {
                Block nb = new Block();
                nb.setDiaryId(diaryId);
                nb.setType(dto.getType());
                nb.setContent(dto.getContent());
                nb.setAttachmentId(dto.getAttachmentId());
                nb.setMetadata(dto.getMetadata());
                nb.setPosition(pos);
                nb.setCreatedAt(LocalDateTime.now());
                nb.setUpdatedAt(LocalDateTime.now());
                nb.setDeletedAt(null);
                toInsert.add(nb);
            }
            pos++;
        }

        if (!toInsert.isEmpty()) {
            try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
                BlockMapper batchMapper = session.getMapper(BlockMapper.class);
                int i = 0;
                for (Block nb : toInsert) {
                    batchMapper.insert(nb);
                    i++;
                    if (i % BATCH_FLUSH_SIZE == 0) {
                        session.flushStatements();
                    }
                }
                session.flushStatements();
            } catch (Exception ex) {
                log.error("batch insert blocks failed", ex);
                throw ex;
            }
        }

        if (!toUpdate.isEmpty()) {
            try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
                BlockMapper batchMapper = session.getMapper(BlockMapper.class);
                int i = 0;
                for (Block ub : toUpdate) {
                    batchMapper.updateById(ub);
                    i++;
                    if (i % BATCH_FLUSH_SIZE == 0) {
                        session.flushStatements();
                    }
                }
                session.flushStatements();
            } catch (Exception ex) {
                log.error("batch update blocks failed", ex);
                throw ex;
            }
        }

        Set<Long> existingIds = existingById.keySet();
        List<Long> toDeleteIds = existingIds.stream()
                .filter(id -> !incomingExistingIds.contains(id))
                .collect(Collectors.toList());

        if (!toDeleteIds.isEmpty()) {
            UpdateWrapper<Block> uw = new UpdateWrapper<>();
            uw.in("id", toDeleteIds).isNull("deleted_at")
                    .set("deleted_at", LocalDateTime.now());
            blockMapper.update(null, uw);
        }

        return getBlocksByDiaryId(diaryId);
    }

    /**
     * 根据 diaryId 做 blocks 的软删除（设置 deleted_at）
     */
    @Transactional
    public void softDeleteBlocksForDiary(Long diaryId) {
        if (diaryId == null) return;
        UpdateWrapper<Block> uw = new UpdateWrapper<>();
        uw.eq("diary_id", diaryId).isNull("deleted_at")
                .set("deleted_at", LocalDateTime.now());
        blockMapper.update(null, uw);
        log.info("Soft deleted blocks for diaryId={}", diaryId);
    }
}