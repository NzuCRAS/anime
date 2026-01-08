/*
Navicat MySQL Dump SQL

Source Server         : anime
Source Server Type    : MySQL
Source Server Version : 80041 (8.0.41)
Source Host           : localhost:3306
Source Schema         : anime-develop

Target Server Type    : MySQL
Target Server Version : 80041 (8.0.41)
File Encoding         : 65001

Date: 07/01/2026 22:31:56
*/
SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;
-- ----------------------------
-- Table structure for abr_session_metrics
-- ----------------------------
DROP TABLE IF EXISTS `abr_session_metrics`;

CREATE TABLE `abr_session_metrics` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `session_id` bigint NOT NULL,
    `avg_bitrate` int NULL DEFAULT NULL COMMENT '平均播放码率bps',
    `startup_delay_ms` int NULL DEFAULT NULL COMMENT '启动延迟ms',
    `rebuffer_count` int NULL DEFAULT NULL COMMENT '重缓冲次数',
    `total_rebuffer_ms` int NULL DEFAULT NULL COMMENT '总重缓冲时长ms',
    `play_duration_ms` int NULL DEFAULT NULL COMMENT '播放时长ms',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_metrics_session` (`session_id` ASC) USING BTREE,
    CONSTRAINT `fk_metrics_session` FOREIGN KEY (`session_id`) REFERENCES `abr_sessions` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for abr_sessions
-- ----------------------------
DROP TABLE IF EXISTS `abr_sessions`;

CREATE TABLE `abr_sessions` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `session_uuid` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '前端/后端生成会话标识',
    `user_id` bigint NOT NULL,
    `video_id` bigint NOT NULL,
    `strategy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'CLIENT_ONLY / SERVER_ASSISTED 等',
    `congestion_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'DIRECT_STREAM / CACHE_PRIORITY 等',
    `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `ended_at` datetime NULL DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_abr_session_uuid` (`session_uuid` ASC) USING BTREE,
    INDEX `idx_abr_session_user` (`user_id` ASC) USING BTREE,
    INDEX `idx_abr_session_video` (`video_id` ASC) USING BTREE,
    CONSTRAINT `fk_abr_session_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_abr_session_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for attachments
-- ----------------------------
DROP TABLE IF EXISTS `attachments`;

CREATE TABLE `attachments` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 's3',
    `bucket` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `storage_key` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
    `checksum` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
    `mime_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
    `size_bytes` bigint NULL DEFAULT NULL,
    `width` int NULL DEFAULT NULL,
    `height` int NULL DEFAULT NULL,
    `uploaded_by` bigint NULL DEFAULT NULL,
    `status` enum(
        'uploading',
        'available',
        'processing',
        'deleted'
    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'uploading',
    `metadata` json NULL,
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_attachments_checksum` (`checksum` ASC) USING BTREE,
    INDEX `idx_attachments_status` (`status` ASC) USING BTREE,
    INDEX `idx_attachments_created_at` (`created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 101 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for blocks
-- ----------------------------
DROP TABLE IF EXISTS `blocks`;

CREATE TABLE `blocks` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `diary_id` bigint NOT NULL,
    `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    `attachment_id` bigint NULL DEFAULT NULL,
    `position` int NOT NULL,
    `metadata` json NULL,
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` timestamp NULL DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `fk_blocks_attachment` (`attachment_id` ASC) USING BTREE,
    INDEX `idx_blocks_diary_position` (
        `diary_id` ASC,
        `position` ASC
    ) USING BTREE,
    CONSTRAINT `fk_blocks_attachment` FOREIGN KEY (`attachment_id`) REFERENCES `attachments` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT `fk_blocks_diary` FOREIGN KEY (`diary_id`) REFERENCES `diaries` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 45 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chat_group_members
-- ----------------------------
DROP TABLE IF EXISTS `chat_group_members`;

CREATE TABLE `chat_group_members` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `group_id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'member' COMMENT 'member/admin/owner',
    `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_group_user` (`group_id` ASC, `user_id` ASC) USING BTREE,
    INDEX `idx_group_members_user` (`user_id` ASC) USING BTREE,
    CONSTRAINT `fk_group_members_group` FOREIGN KEY (`group_id`) REFERENCES `chat_groups` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_group_members_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for chat_groups
-- ----------------------------
DROP TABLE IF EXISTS `chat_groups`;

CREATE TABLE `chat_groups` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `owner_id` bigint NOT NULL COMMENT '群主用户ID',
    `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_chat_groups_owner` (`owner_id` ASC) USING BTREE,
    CONSTRAINT `fk_chat_groups_owner` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for chat_messages
-- ----------------------------
DROP TABLE IF EXISTS `chat_messages`;

CREATE TABLE `chat_messages` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `client_message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
    `logic_message_id` bigint NULL DEFAULT NULL COMMENT '逻辑消息ID，同一条消息对所有接收者共用同一ID',
    `conversation_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PRIVATE / GROUP',
    `from_user_id` bigint NOT NULL,
    `to_user_id` bigint NULL DEFAULT NULL COMMENT '私聊时为对方ID，群聊时为NULL',
    `group_id` bigint NULL DEFAULT NULL COMMENT '群聊时为群ID，私聊时为NULL',
    `message_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'TEXT / IMAGE',
    `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '文本内容',
    `attachment_id` bigint NULL DEFAULT NULL COMMENT '图片等附件ID，关联attachments',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted_at` datetime NULL DEFAULT NULL,
    `is_read` tinyint(1) NOT NULL DEFAULT 0 COMMENT '仅对私聊有效：0=未读, 1=已读',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uq_chat_messages_from_client` (
        `from_user_id` ASC,
        `client_message_id` ASC
    ) USING BTREE,
    INDEX `idx_chat_messages_private` (
        `conversation_type` ASC,
        `from_user_id` ASC,
        `to_user_id` ASC,
        `created_at` ASC
    ) USING BTREE,
    INDEX `idx_chat_messages_group` (
        `conversation_type` ASC,
        `group_id` ASC,
        `created_at` ASC
    ) USING BTREE,
    INDEX `idx_chat_messages_from` (`from_user_id` ASC) USING BTREE,
    INDEX `fk_chat_messages_to_user` (`to_user_id` ASC) USING BTREE,
    INDEX `fk_chat_messages_group` (`group_id` ASC) USING BTREE,
    INDEX `fk_chat_messages_attachment` (`attachment_id` ASC) USING BTREE,
    CONSTRAINT `fk_chat_messages_attachment` FOREIGN KEY (`attachment_id`) REFERENCES `attachments` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_chat_messages_from_user` FOREIGN KEY (`from_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_chat_messages_group` FOREIGN KEY (`group_id`) REFERENCES `chat_groups` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_chat_messages_to_user` FOREIGN KEY (`to_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 34 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for collected_items
-- ----------------------------
DROP TABLE IF EXISTS `collected_items`;

CREATE TABLE `collected_items` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `attachment_id` bigint NULL DEFAULT NULL,
    `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_modified_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `folder_level2_id` bigint NOT NULL COMMENT '所属二级收藏夹ID，不可为空',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_folder_level2_id` (`folder_level2_id` ASC) USING BTREE,
    INDEX `fk_collected_items_attachment` (`attachment_id` ASC) USING BTREE,
    CONSTRAINT `fk_collected_item_folder2` FOREIGN KEY (`folder_level2_id`) REFERENCES `collection_folders_level2` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_collected_items_attachment` FOREIGN KEY (`attachment_id`) REFERENCES `attachments` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 230 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for collection_folders_level1
-- ----------------------------
DROP TABLE IF EXISTS `collection_folders_level1`;

CREATE TABLE `collection_folders_level1` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `attachment_id` bigint NULL DEFAULT NULL COMMENT '作为attachments表的外键',
    `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `user_id` bigint NOT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_user_id` (`user_id` ASC) USING BTREE,
    INDEX `fk_level1_attachment` (`attachment_id` ASC) USING BTREE,
    CONSTRAINT `fk_level1_attachment` FOREIGN KEY (`attachment_id`) REFERENCES `attachments` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_level1_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for collection_folders_level2
-- ----------------------------
DROP TABLE IF EXISTS `collection_folders_level2`;

CREATE TABLE `collection_folders_level2` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `parent_folder_id` bigint NOT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_parent_folder_id` (`parent_folder_id` ASC) USING BTREE,
    CONSTRAINT `fk_level2_parent` FOREIGN KEY (`parent_folder_id`) REFERENCES `collection_folders_level1` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 128 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for diaries
-- ----------------------------
DROP TABLE IF EXISTS `diaries`;

CREATE TABLE `diaries` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `version` bigint NOT NULL DEFAULT 0,
    `deleted_at` timestamp NULL DEFAULT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_diaries_user_id` (`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for friend_requests
-- ----------------------------
DROP TABLE IF EXISTS `friend_requests`;

CREATE TABLE `friend_requests` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `from_user_id` bigint NOT NULL,
    `to_user_id` bigint NOT NULL,
    `message` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
    `status` enum(
        'pending',
        'accepted',
        'rejected'
    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_request_from_to` (
        `from_user_id` ASC,
        `to_user_id` ASC
    ) USING BTREE,
    INDEX `idx_friend_requests_to` (`to_user_id` ASC) USING BTREE,
    CONSTRAINT `fk_friend_request_from_user` FOREIGN KEY (`from_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_friend_request_to_user` FOREIGN KEY (`to_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for schedules
-- ----------------------------
DROP TABLE IF EXISTS `schedules`;

CREATE TABLE `schedules` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `event_time` datetime NOT NULL,
    `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `is_completed` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0: 未完成, 1: 已完成',
    `user_id` bigint NOT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_user_id` (`user_id` ASC) USING BTREE,
    INDEX `idx_event_time` (`event_time` ASC) USING BTREE,
    CONSTRAINT `fk_schedule_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_friends
-- ----------------------------
DROP TABLE IF EXISTS `user_friends`;

CREATE TABLE `user_friends` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '当前用户ID',
    `friend_id` bigint NOT NULL COMMENT '好友用户ID',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_user_friend` (
        `user_id` ASC,
        `friend_id` ASC
    ) USING BTREE,
    INDEX `idx_friend_user` (`friend_id` ASC) USING BTREE,
    CONSTRAINT `fk_friend_friend` FOREIGN KEY (`friend_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_friend_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;

CREATE TABLE `users` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `avatar_attachment_id` bigint NULL DEFAULT NULL,
    `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_login` datetime NULL DEFAULT NULL,
    `personal_signature` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_users_username` (`username` ASC) USING BTREE,
    UNIQUE INDEX `uk_users_email` (`email` ASC) USING BTREE,
    INDEX `idx_users_last_login` (`last_login` ASC) USING BTREE,
    INDEX `avatar_url` (`avatar_attachment_id` ASC) USING BTREE,
    CONSTRAINT `avatar_url` FOREIGN KEY (`avatar_attachment_id`) REFERENCES `attachments` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_likes
-- ----------------------------
DROP TABLE IF EXISTS `video_likes`;

CREATE TABLE `video_likes` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `video_id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    `active` tinyint(1) NOT NULL DEFAULT 1,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_video_user` (`video_id` ASC, `user_id` ASC) USING BTREE,
    INDEX `idx_likes_user` (`user_id` ASC) USING BTREE,
    CONSTRAINT `fk_likes_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_likes_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_transcodes
-- ----------------------------
DROP TABLE IF EXISTS `video_transcodes`;

CREATE TABLE `video_transcodes` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `video_id` bigint NOT NULL,
    `representation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '如240p,480p,720p',
    `bitrate` int NOT NULL COMMENT '码率bps',
    `resolution` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分辨率,如854x480',
    `manifest_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '子playlist或DASH路径',
    `segment_base_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分片路径前缀',
    `status` enum(
        'processing',
        'ready',
        'failed'
    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'processing',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_transcodes_video` (`video_id` ASC) USING BTREE,
    INDEX `idx_transcodes_status` (`status` ASC) USING BTREE,
    CONSTRAINT `fk_transcodes_video` FOREIGN KEY (`video_id`) REFERENCES `videos` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for videos
-- ----------------------------
DROP TABLE IF EXISTS `videos`;

CREATE TABLE `videos` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `uploader_id` bigint NOT NULL COMMENT '上传者 users.id',
    `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    `source_attachment_id` bigint NOT NULL COMMENT '原始MP4在attachments中的ID',
    `cover_attachment_id` bigint NULL DEFAULT NULL COMMENT '封面图在attachments中的ID',
    `status` enum(
        'uploading',
        'processing',
        'ready',
        'failed'
    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'uploading',
    `duration_sec` int NULL DEFAULT NULL COMMENT '视频时长（秒）',
    `like_count` int NOT NULL DEFAULT 0,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_videos_uploader` (`uploader_id` ASC) USING BTREE,
    INDEX `idx_videos_status` (`status` ASC) USING BTREE,
    INDEX `fk_videos_source_attachment` (`source_attachment_id` ASC) USING BTREE,
    INDEX `fk_videos_cover_attachment` (`cover_attachment_id` ASC) USING BTREE,
    CONSTRAINT `fk_videos_cover_attachment` FOREIGN KEY (`cover_attachment_id`) REFERENCES `attachments` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_videos_source_attachment` FOREIGN KEY (`source_attachment_id`) REFERENCES `attachments` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_videos_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- View structure for attachment_refs
-- ----------------------------
DROP VIEW IF EXISTS `attachment_refs`;

CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `attachment_refs` AS
select `a`.`id` AS `attachment_id`, coalesce(sum(`t`.`refs`), 0) AS `refs`
from (
        `attachments` `a`
        left join (
            select `blocks`.`attachment_id` AS `aid`, count(0) AS `refs`
            from `blocks`
            where (
                    (
                        `blocks`.`attachment_id` is not null
                    )
                    and (`blocks`.`deleted_at` is null)
                )
            group by
                `blocks`.`attachment_id`
            union all
            select `collected_items`.`attachment_id` AS `aid`, count(0) AS `refs`
            from `collected_items`
            where (
                    `collected_items`.`attachment_id` is not null
                )
            group by
                `collected_items`.`attachment_id`
            union all
            select `collection_folders_level1`.`attachment_id` AS `aid`, count(0) AS `refs`
            from `collection_folders_level1`
            where (
                    `collection_folders_level1`.`attachment_id` is not null
                )
            group by
                `collection_folders_level1`.`attachment_id`
        ) `t` on ((`a`.`id` = `t`.`aid`))
    )
group by
    `a`.`id`;

SET FOREIGN_KEY_CHECKS = 1;