package com.anime.common.entity.diary;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Block 实体：对应数据库表 blocks
 *
 * 说明：
 * - type: 'text', 'image', 'embed' 等
 * - content: 文本内容（对 text block 有值）；对于 image block，content 通常为空，attachmentId 指向 attachments
 * - attachmentId: 关联 attachments.id（nullable）
 * - position: 从 1 开始的连续编号（在一次性保存时，由后端按前端顺序重编号）
 * - metadata: JSON 原文字符串（可选；若要在 MyBatis 中自动映射为 Map/POJO，可实现 TypeHandler）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("blocks")
public class Block {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long diaryId;

    private String type;

    private String content;

    private Long attachmentId;

    /**
     * 连续编号，从1开始。按前端顺序在保存时重写。
     */
    private Integer position;

    /**
     * JSON 字符串（例如 {"caption":"...", "alt":"...", "layout":"center"}）
     */
    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}