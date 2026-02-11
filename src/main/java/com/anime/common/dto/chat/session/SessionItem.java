package com.anime.common.dto.chat.session;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单个会话条目（左侧列表的一行）
 */
@Data
public class SessionItem {

    /**
     * 会话类型：PRIVATE 表示单聊；GROUP 表示群聊
     */
    private String sessionType;

    /**
     * 会话唯一 ID：
     * - 单聊：建议用对方 userId
     * - 群聊：用 groupId
     */
    private Long sessionTargetId;

    /**
     * 会话显示标题：
     * - 单聊：好友昵称 / 用户名
     * - 群聊：群名称
     */
    private String title;

    /**
     * 最后一条消息的简要内容（截断后的前一部分）
     */
    private String lastMessagePreview;

    /**
     * 最后一条消息的发送时间
     */
    private LocalDateTime lastMessageTime;

    /**
     * 当前会话的未读消息数量
     */
    private Integer unreadCount;

    /**
     * 该用户的个性签名（适配群描述）
     */
    private String signature;

    /**
     * 会话头像：
     * - 单聊：对方用户头像 URL
     * - 群聊：群头像 URL（可选）
     */
    private String avatarUrl;

    /**
     * 用户是否在线
     */
    private boolean isOnline;
}