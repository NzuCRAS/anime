package com.anime.common.mapper.chat;

import com.anime.common.entity.chat.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询私聊历史消息（按时间倒序分页）
     *
     * 注意：过滤已逻辑删除的记录（deleted_at IS NULL）
     */
    @Select("""
        SELECT * FROM chat_messages
        WHERE conversation_type = 'PRIVATE'
          AND deleted_at IS NULL
          AND (
                (from_user_id = #{userId} AND to_user_id = #{friendId})
             OR (from_user_id = #{friendId} AND to_user_id = #{userId})
          )
        ORDER BY created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<ChatMessage> listPrivateMessages(Long userId, Long friendId, int limit, int offset);

    /**
     * 查询群聊历史消息（当前用户视角）
     *
     * 注意：过滤 deleted_at，并确保是当前用户的视角（to_user_id = 当前用户）
     */
    @Select("""
        SELECT * FROM chat_messages
        WHERE conversation_type = 'GROUP'
          AND deleted_at IS NULL
          AND group_id = #{groupId}
          AND to_user_id = #{currentUserId}
        ORDER BY created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<ChatMessage> listGroupMessages(Long groupId, Long currentUserId, int limit, int offset);

    /**
     * 查询当前用户参与的所有私聊消息（自己发出的或别人发给自己的），按时间倒序。
     * 后续在 Service 中按“对方 userId”分组，取每个会话的最新一条。
     *
     * 注意：过滤 deleted_at
     */
    @Select("""
        SELECT *
        FROM chat_messages
        WHERE conversation_type = 'PRIVATE'
          AND deleted_at IS NULL
          AND (from_user_id = #{userId} OR to_user_id = #{userId})
        ORDER BY created_at DESC
        """)
    List<ChatMessage> listAllPrivateMessagesForUser(Long userId);

    /**
     * 查询当前用户所在所有群里的全部群聊消息（按时间倒序）。
     *
     * 逻辑：
     *  - 先在 chat_group_members 表中找出该用户加入的所有 group_id
     *  - 再查这些 group_id 对应“发给该用户”的消息
     *
     * 注意：过滤 deleted_at，并且限定 to_user_id = 当前用户
     */
    @Select("""
        SELECT *
        FROM chat_messages
        WHERE conversation_type = 'GROUP'
          AND deleted_at IS NULL
          AND group_id IN (
              SELECT group_id
              FROM chat_group_members
              WHERE user_id = #{userId}
          )
          AND to_user_id = #{userId}
        ORDER BY created_at DESC
        """)
    List<ChatMessage> listAllGroupMessagesForUser(Long userId);

    /**
     * 将当前用户作为接收方，来自指定好友的未读私聊消息全部标记为已读。
     *
     * 注意：只更新未删除的记录
     */
    @Update("""
        UPDATE chat_messages
        SET is_read = 1
        WHERE conversation_type = 'PRIVATE'
          AND deleted_at IS NULL
          AND to_user_id = #{currentUserId}
          AND from_user_id = #{friendId}
          AND is_read = 0
        """)
    int markPrivateMessagesRead(Long currentUserId, Long friendId);

    /**
     * 当前用户对某条消息执行逻辑删除。
     *
     * 由于现在是一人一条记录，所以只会删当前用户视角那条。
     */
    @Update("""
        UPDATE chat_messages
        SET deleted_at = NOW()
        WHERE id = #{messageId}
          AND (
               from_user_id = #{userId}
            OR to_user_id = #{userId}
          )
          AND deleted_at IS NULL
        """)
    int deleteMessageForUser(Long userId, Long messageId);

    /**
     * 查询当前用户作为接收方的所有私聊未读消息数量，
     * 按发送方分组。
     *
     * 返回每个好友的未读数量（from_user_id, unread_count）。
     *
     * 注意：只统计未删除的记录
     */
    @Select("""
        SELECT from_user_id AS friendId,
               COUNT(*)      AS unreadCount
        FROM chat_messages
        WHERE conversation_type = 'PRIVATE'
          AND deleted_at IS NULL
          AND to_user_id = #{currentUserId}
          AND is_read = 0
        GROUP BY from_user_id
        """)
    List<java.util.Map<String, Object>> listPrivateUnreadCountsByFriend(Long currentUserId);

    /**
     * 查询当前用户在各个群聊中的未读消息数量。
     *
     * 返回：groupId -> unreadCount
     */
    @Select("""
        SELECT group_id AS groupId,
               COUNT(*)  AS unreadCount
        FROM chat_messages
        WHERE conversation_type = 'GROUP'
          AND to_user_id = #{currentUserId}
          AND deleted_at IS NULL
          AND is_read = 0
        GROUP BY group_id
        """)
    List<GroupUnreadCountRow> listGroupUnreadCountsByGroup(Long currentUserId);

    /**
     * 将当前用户在指定群的群聊消息全部标记为已读。
     */
    @Update("""
        UPDATE chat_messages
        SET is_read = 1
        WHERE conversation_type = 'GROUP'
          AND to_user_id = #{currentUserId}
          AND group_id = #{groupId}
          AND deleted_at IS NULL
          AND is_read = 0
        """)
    int markGroupMessagesRead(Long currentUserId, Long groupId);

    /**
     * 根据发送者和 clientMessageId 查找已存在的记录（返回所有匹配记录）
     * - 用于幂等检测：如果存在，说明该逻辑消息已被插入（可能包含发送者视角与接收者视角多条记录）
     */
    @Select("SELECT * FROM chat_messages WHERE from_user_id = #{fromUserId} AND client_message_id = #{clientMessageId}")
    List<ChatMessage> selectByFromAndClientId(@Param("fromUserId") Long fromUserId,
                                              @Param("clientMessageId") String clientMessageId);

    /**
     * 查询某用户与某好友之间的最新一条私聊消息（按时间倒序取 1 条）
     *
     * 注意：这里仍然是“消息表”的记录（可能是本人视角或对方视角），
     * 用于会话列表展示 lastMessagePreview/lastMessageTime
     */
    @Select("""
        SELECT *
        FROM chat_messages
        WHERE conversation_type = 'PRIVATE'
          AND deleted_at IS NULL
          AND (
                (from_user_id = #{userId} AND to_user_id = #{friendId})
             OR (from_user_id = #{friendId} AND to_user_id = #{userId})
          )
        ORDER BY created_at DESC
        LIMIT 1
        """)
    ChatMessage findLastPrivateMessage(@Param("userId") Long userId,
                                       @Param("friendId") Long friendId);

    /**
     * 查询某用户作为接收方，来自指定好友的未读私聊消息数量
     */
    @Select("""
        SELECT COUNT(*)
        FROM chat_messages
        WHERE conversation_type = 'PRIVATE'
          AND deleted_at IS NULL
          AND to_user_id = #{userId}
          AND from_user_id = #{friendId}
          AND is_read = 0
        """)
    Long countPrivateUnread(@Param("userId") Long userId,
                            @Param("friendId") Long friendId);


    @Select("""
    SELECT id
    FROM chat_messages
    WHERE conversation_type = 'PRIVATE'
      AND deleted_at IS NULL
      AND from_user_id = #{friendId}    -- 对方发给我的消息
      AND to_user_id = #{currentUserId}
      AND is_read = 1
    ORDER BY created_at DESC
    LIMIT 1
    """)
    Long findLastReadMessageIdBetween(@Param("currentUserId") Long currentUserId,
                                      @Param("friendId") Long friendId);

    /**
     * 查询当前用户在某个群中的最新一条群聊消息（按时间倒序取 1 条）
     *
     * 注意：
     * - 这里用的是“当前用户视角”的记录（to_user_id = currentUserId）
     * - 用于会话列表里的 lastMessagePreview/lastMessageTime
     */
    @Select("""
        SELECT *
        FROM chat_messages
        WHERE conversation_type = 'GROUP'
          AND deleted_at IS NULL
          AND group_id = #{groupId}
          AND to_user_id = #{currentUserId}
        ORDER BY created_at DESC
        LIMIT 1
        """)
    ChatMessage findLastGroupMessage(@Param("groupId") Long groupId,
                                     @Param("currentUserId") Long currentUserId);

    /**
     * 查询当前用户在某个群中的未读消息数量
     */
    @Select("""
        SELECT COUNT(*)
        FROM chat_messages
        WHERE conversation_type = 'GROUP'
          AND deleted_at IS NULL
          AND group_id = #{groupId}
          AND to_user_id = #{currentUserId}
          AND is_read = 0
        """)
    Long countGroupUnread(@Param("groupId") Long groupId,
                          @Param("currentUserId") Long currentUserId);

    /**
     * 撤回：根据逻辑消息ID，批量逻辑删除该逻辑消息下的所有记录
     */
    @Update("""
        UPDATE chat_messages
        SET deleted_at = NOW()
        WHERE logic_message_id = #{logicMessageId}
          AND deleted_at IS NULL
        """)
    int recallByLogicId(@Param("logicMessageId") Long logicMessageId);

    /**
     * 找到该逻辑消息涉及到的所有用户（以各记录的 to_user_id 为准，包含发送者自己的视角记录）
     */
    @Select("""
        SELECT DISTINCT to_user_id
        FROM chat_messages
        WHERE logic_message_id = #{logicMessageId}
        """)
    List<Long> listRecipientsByLogicId(@Param("logicMessageId") Long logicMessageId);

    interface GroupUnreadCountRow {
        Long getGroupId();
        Long getUnreadCount();
    }
}