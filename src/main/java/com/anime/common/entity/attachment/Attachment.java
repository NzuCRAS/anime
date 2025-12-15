package com.anime.common.entity.attachment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Attachment 实体：对应数据库表 attachments
 *
 * 说明：
 * - storageKey: 对象存储中的 key/path（必填）
 * - url: 可选的 CDN 或公开 URL（若使用 CDN，可把 cdnDomain + storageKey 写入）
 * - status: uploading / available / processing / deleted
 * - metadata: JSON 原文（缩略图 variants、exif 等）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("attachments")
public class Attachment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String provider;

    private String bucket;

    private String storageKey;

    private String url;

    // 校验码(可以用于检验文件完整性)
    private String checksum;

    // 拓展名
    private String mimeType;

    private Long sizeBytes;

    private Integer width;

    private Integer height;

    private Long uploadedBy;

    /**
     * 状态：uploading / available / processing / deleted
     */
    private String status;

    /**
     * JSON 字符串（variants、exif等）
     */
    private String metadata;

    private LocalDateTime createdAt;
}