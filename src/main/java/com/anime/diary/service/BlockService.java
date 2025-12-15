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
 *
 * 说明：
 * - 批量插入/更新使用 MyBatis 的 BATCH Executor（通过 SqlSessionFactory.openSession(ExecutorType.BATCH)）。
 * - 软删除使用单次 UpdateWrapper IN(...) 操作以减少 round-trips。
 * - 事务注解仍然保留，确保操作在同一事务边界内提交（注意：BATCH 模式需慎重与事务超时）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockMapper blockMapper;
    private final SqlSessionFactory sqlSessionFactory;

    private static final int BATCH_FLUSH_SIZE = 100;

    /**
     * 获取 diary 的 blocks（未删除）
     */
    public List<Block> getBlocksByDiaryId(Long diaryId) {
        QueryWrapper<Block> qw = new QueryWrapper<>();
        qw.eq("diary_id", diaryId)
                .isNull("deleted_at")
                .orderByAsc("position");
        return blockMapper.selectList(qw);
    }

    /**
     * 保存 diary 下的所有 blocks（按前端顺序重写 position）。
     * 批量插入/更新以提升性能（减少 DB round-trips）。
     */
    @Transactional
    public List<Block> saveBlocksForDiary(Long diaryId, List<BlockDTO> blocks) {
        if (diaryId == null) throw new IllegalArgumentException("diaryId required");
        if (blocks == null) blocks = Collections.emptyList();

        // 1) 读取数据库中已有的非删除 blocks
        List<Block> existing = getBlocksByDiaryId(diaryId);
        Map<Long, Block> existingById = existing.stream()
                .filter(b -> b.getId() != null)
                .collect(Collectors.toMap(Block::getId, b -> b));

        Set<Long> incomingExistingIds = new HashSet<>();
        List<Block> toInsert = new ArrayList<>();
        List<Block> toUpdate = new ArrayList<>();

        // 2) 按传入顺序处理并重新分配 position（从1开始）
        int pos = 1;
        for (BlockDTO dto : blocks) {
            Long incomingId = dto.getBlockId();
            if (incomingId != null && existingById.containsKey(incomingId)) {
                // 更新已有记录（收集到 toUpdate）
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
                // 新记录：准备批量插入
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

        // 3) 批量插入新记录（使用 MyBatis BATCH executor to reduce round trips）
        if (!toInsert.isEmpty()) {
            // 使用 SqlSession 的 BATCH 模式批量执行 mapper.insert(...)
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
                // 注意：不在这里 session.commit()，因为方法上有 @Transactional，Spring 会接管事务并提交
            } catch (Exception ex) {
                log.error("batch insert blocks failed", ex);
                throw ex;
            }
            // 经过批量插入后，新实体的 id 虽然已由 DB 生成，但回填到 toInsert 中是否发生取决于 MyBatis 配置。
            // 为了确保返回结果准确，我们最后从 DB 重新查询并返回。
        }

        // 4) 批量更新已有记录（同样使用 BATCH 模式）
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

        // 5) 对 DB 中存在但未出现在 incoming 列表中的记录做软删除（单次 update with IN）
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

        // 6) 为确保返回数据完整（包含自增 id），从 DB 重新查询并返回
        return getBlocksByDiaryId(diaryId);
    }
}