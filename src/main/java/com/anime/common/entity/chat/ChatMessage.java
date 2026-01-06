package com.anime.common.entity.chat;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_messages")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 逻辑消息ID：
     * - 同一条逻辑消息对多个接收者的多条记录共用一个值
     * - 一般设为“第一条插入记录的 id”
     */
    private Long logicMessageId;

    /**
     * 会话类型：PRIVATE / GROUP
     */
    private String conversationType;

    /**
     * 发送者ID（users.id）
     */
    private Long fromUserId;

    /**
     * 接收者ID：
     * - 私聊：对方ID
     * - 群聊：每个群成员一条记录，对应成员ID
     */
    private Long toUserId;

    /**
     * 群聊时为群ID；私聊时为NULL
     */
    private Long groupId;

    /**
     * 消息类型：TEXT / IMAGE
     */
    private String messageType;

    /**
     * 文本内容，图片消息可为空
     */
    private String content;

    /**
     * 附件ID（图片等，attachments.id）
     */
    private Long attachmentId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 逻辑删除时间：
     * - NULL: 未删除
     * - 非 NULL: 该接收者视角已删除
     */
    private LocalDateTime deletedAt;

    /**
     * 是否已读（对该接收方）：
     * 0 = 未读
     * 1 = 已读
     */
    private Integer isRead;
}