接口：

```/api/chat/sessions/list```

无传入内容

返回内容：

参考json：

```json
{
  "sessions":[
    { "sessionTargetId"(也就是用户id):"4", "sessionType":"PRIVATE", "title"(也就是好友名称):"name", "lastMessage"(如果是图片就显示“[图片]”):"Hi", "lastMessageTime":"2026-01-06 16:00:00", "unreadCount":2 , "signature": "个性签名", "avatarUrl" :"一串getUrl"},
    { "sessionTargetId":"5", "sessionType":"PRIVATE", "title":"name", "lastMessage":"Hi", "lastMessageTime":"2026-01-06 16:00:00", "unreadCount":103 , "signature": "个性签名", "avatarUrl" :"一串getUrl"}
  ]
}
```

ListSessionsResponse：

```

public class ListSessionsResponse {

    /**
     * 当前用户所有会话（单聊 + 群聊），
     * 已按 lastMessageTime 从新到旧排序
     */
    private List<SessionItem> sessions;
}

```

每一个SessionItem ：

```

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
}

```