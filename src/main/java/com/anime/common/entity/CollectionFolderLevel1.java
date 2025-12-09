package com.anime.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一级收藏夹实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("collection_folders_level1")
public class CollectionFolderLevel1 {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 收藏夹名称
     */
    private String name;

    /**
     * 封面图片路径
     */
    @TableField("attachment_id")
    private Long attachmentId;

    /**
     * 创建时间
     */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 用户ID（外键）
     */
    @TableField("user_id")
    private Long userId;
}