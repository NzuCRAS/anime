package com.anime.common.entity.collection;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收藏项实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("collected_items")
public class CollectedItem {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 封面图片路径
     */
    @TableField("attachment_id")
    private Long attachmentId;

    /**
     * 收藏项名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 最后修改时间
     */
    @TableField(value = "last_modified_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastModifiedTime;

    /**
     * 所属二级收藏夹ID（外键）
     */
    @TableField("folder_level2_id")
    private Long folderLevel2Id;
}