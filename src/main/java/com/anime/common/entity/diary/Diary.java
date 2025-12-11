package com.anime.common.entity.diary;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Diary 实体：对应数据库表 diaries
 *
 * 注意：
 * - version 用于乐观锁（后端使用 UPDATE ... WHERE id=? AND version=? 或 SELECT FOR UPDATE + version++）
 * - deletedAt 为软删除时间，如非 null 表示已删除
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("diaries")
public class Diary {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * 乐观锁版本号（后端在保存时需验证并自增）
     */
    private Long version;

    /**
     * 软删除时间，null 表示未删除
     */
    private LocalDateTime deletedAt;
}