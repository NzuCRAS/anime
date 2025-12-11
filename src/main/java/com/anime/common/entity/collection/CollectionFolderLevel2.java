package com.anime.common.entity.collection;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 二级收藏夹实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("collection_folders_level2")
public class CollectionFolderLevel2 {

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
     * 父级收藏夹ID（外键，关联一级收藏夹）
     */
    @TableField("parent_folder_id")
    private Long parentFolderId;
}