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
 * 说明：新增 transient 字段 attachmentUrl（不持久化）用于返回给前端展示（image 类型的短期下载 URL）。
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

    /**
     * 运行时使用：当 block.type == "image" 且 attachmentId != null 时，后端会动态生成短期可用的下载 URL（presigned/get）
     * 该字段不应被 MyBatis 持久化（transient），仅用于 response payload。
     */
    private transient String attachmentUrl;
}