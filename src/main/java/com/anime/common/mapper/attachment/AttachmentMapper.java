package com.anime.common.mapper.attachment;

import com.anime.common.entity.attachment.Attachment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AttachmentMapper
 * 基于 MyBatis-Plus 的基础 Mapper，用于 attachments 表的 CRUD 操作。
 *
 * 如需自定义复杂 SQL，可以在此接口中添加方法并配套 XML 或使用注解方式实现。
 */
@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
    // 可在此添加自定义方法签名，例如：
    // List<Attachment> selectUnusedAttachments(...);
}