# API 文档 — Video 模块 & Chat 模块

本文件以 Markdown 形式描述当前项目中 Video（视频）模块与 Chat（即时通讯：好友/群/消息/会话）模块的 HTTP 接口。每个接口包含：

- URL 与 HTTP 方法
- 认证要求
- 请求（示例 JSON、字段说明）
- 响应（示例 JSON、字段说明）
- 行为说明 / 注意事项
- 可选：curl 示例或调用说明

通用说明
- 绝大多数接口要求认证（Bearer token）。将 access token 放在 HTTP Header：
  Authorization: Bearer <access_token>
- 所有响应均按统一包装结构 `Result<T>` 返回，示例：
```json
{
  "isSuccess": true,
  "code": 200,
  "message": "操作成功",
  "data": { ... },
  "timestamp": "2026-01-06 20:00:00"
}
```
- 时间格式：`yyyy-MM-dd HH:mm:ss` 或 ISO-8601（视实现）。
- 请在跨域（浏览器）场景下确保后端与 MinIO 的 CORS 配置允许所需方法与请求头（尤其 PUT/GET、Authorization、Content-Type）。

---

# Video 模块接口

基础实体（概念摘要）
- Video
  - id, uploaderId, title, description, sourceAttachmentId, coverAttachmentId, status (`uploading`/`processing`/`ready`/`failed`/`deleted`), durationSec, likeCount, createdAt, updatedAt
- VideoTranscode
  - id, videoId, representationId (`1080p`/`720p`/`360p`/`240p`), bitrate (bps), resolution (`1280x720`), manifestPath (S3 key), segmentBasePath, status (`processing`/`ready`/`failed`)

---

## 1) POST /api/videos/presign
生成一个 presigned PUT URL 并在 attachments 表插入记录（用于前端直传文件）。

认证
- 需登录（@CurrentUser 可选），但可根据权限开放。

请求（JSON）
```json
{
  "originalFilename": "video.mp4",
  "mimeType": "video/mp4"
}
```
字段说明
- originalFilename: 前端上传文件的原名（用于记录）
- mimeType: 文件的 MIME 类型（用于签名）

响应（data = PresignResponseDTO）
```json
{
  "id": 94,
  "putUrl": "https://.../presigned-put-url",
  "putHeaders": { "Content-Type": "video/mp4" },
  "getUrl": null
}
```
说明
- 前端拿到 `putUrl` 后做 PUT 上传（注意浏览器 CORS）。
- `id` 为 attachmentId，用于后续 create video 时作为 sourceAttachmentId。
- 若后端需要完成上传（completeUpload）请在 PUT 成功后调用相应接口（或创建 video 时后端会读取 S3 head）。

示例 curl
```bash
curl -X POST https://api.example.com/api/videos/presign \
  -H "Content-Type: application/json" \
  -d '{"originalFilename":"a.mp4","mimeType":"video/mp4"}'
```

---

## 2) POST /api/videos
创建视频记录（用户填写元数据后调用）。创建后后端会自动异步启动转码任务（@Async / worker）。

认证
- 需要登录（@CurrentUser）

请求（CreateVideoRequest）

```json
{
  "uploaderId": 123,                 // 可选，优先使用 @CurrentUser
  "sourceAttachmentId": 94,          // 必填：前面 presign 上传得到的 id
  "coverAttachmentId": 101,          // 可选
  "title": "My video",
  "description": "..." 
}
```

响应（data）
```json
{ "videoId": 42 }
```

行为说明
- 在 DB 写入 videos 记录并把 `status = processing`。
- 在后台线程（TranscodeWorker）中，探测源分辨率并生成 HLS，多清晰度（默认 1080/720/360/240），但只为低于等于源分辨率的档位生成记录与分片。
- 转码完成后更新 `video_transcodes.manifestPath` 与 `videos.status = ready`。

---

## 3) POST /api/videos/list
查询所有视频（当前实现不分页）。

认证
- 可选（公开列表通常可见）

请求
- 空 body 或 {}

响应（data = List<VideoSummary>）
每项示例：
```json
{
  "id": 42,
  "title": "My video",
  "description": "...",
  "likeCount": 3,
  "createdAt": "2026-01-06 16:00:00",
  "updatedAt": "...",
  "uploaderId": 123,
  "status": "ready"
}
```

---

## 4) POST /api/videos/{videoId}/get
获取单个视频详细信息（播放页用）。

认证
- 可选（视业务）

请求
- 空 body

响应（data）
```json
{
  "video": { /* Video object */ },
  "transcodes": [
    { "id": 7, "representationId":"720p", "bitrate":1800000, "resolution":"1280x720", "manifestPath":"videos/42/hls/stream_1.m3u8", "status":"ready" },
    ...
  ]
}
```
说明
- `manifestPath` 为 S3 存储 key（非 URL）；可用后端生成 presigned GET 或走 HLS proxy。

---

## 5) POST /api/videos/{videoId}/delete
软删除视频（仅 uploader 可操作）。

认证
- 需要登录（必须是 uploader）

请求
- 空 body

响应
- Result.success("ok") 或错误 code（403/404）

后端行为
- 将 `videos.status = deleted`，并把该视频的 transcode 记录标为 failed（或删除，按实现）。

---

## 6) POST /api/videos/{videoId}/like
切换点赞（首次插入记录，后续修改 active 字段；同时更新 videos.like_count）

认证
- 需要登录

请求
- 空 body

响应（data）
```json
{ "liked": true, "likeCount": 10 }
```
字段说明
- liked: 当前用户执行该接口后的点赞状态（true 表示已点赞）
- likeCount: 当前视频的点赞计数（active = 1 的数）

实现要点
- 新增 `video_likes.active` 字段（0/1），首次点赞插入记录，后续更新 active 值；并用 database atomic increment/decrement 同步 `videos.like_count`。

---

## 7) POST /api/videos/{videoId}/playHls
（备用）直接返回 master.m3u8 的 presigned GET URL（不推荐用于前端直接播放，因为 variant & segments 仍需签名）。

认证
- 可选

请求
- 空 body

响应（data）
```json
{ "url": "https://minio/.../master.m3u8?X-Amz-..." }
```

注意
- 推荐使用下面的代理接口 `/hls/playlist`，以便在后端重写 playlist 中的段为 presigned URLs。

---

## 8) GET /api/videos/{videoId}/hls/playlist?name={name}&expiry={s}
HLS playlist 代理/重写接口（推荐的播放集成方式）

认证
- 推荐允许匿名（permitAll），但可根据需要要求认证。因为分片由 presigned URL 控制，playlist 本身可公开。

请求（Query）
- name: playlist 名称（master.m3u8 或 stream_0.m3u8）
- expiry: presigned URL 有效期（秒），后端会在生成 segment presigned URL 时使用该值（最小 60s）

响应
- Content-Type: application/vnd.apple.mpegurl
- Body: M3U8 文本（已重写）
  - master.m3u8: variant 引用替换为后端同接口的 proxy URL（so browser will call proxy for each variant）
  - variant playlist: each segment URI replaced with presigned GET URL to S3

示例 master line rewritten:
```
/api/videos/42/hls/playlist?name=stream_0.m3u8&expiry=300
```

示例 variant line rewritten:
```
https://minio-host/test-bucket/videos/42/hls/stream_0_000.ts?X-Amz-...
```

播放流程说明
1. 前端（hls.js）加载 master via backend proxy.
2. hls.js requests variant via proxy; backend returns variant playlist with segment full presigned URLs.
3. Browser downloads segments directly from S3 via presigned URLs and plays.

注意
- 确保 MinIO CORS 允许前端 origin 从浏览器通过 presigned GET 下载分片（GET）。
- 若你的 security 要求 playlist 受保护，可以让 hls.js 在 xhrSetup 中添加 Authorization header。

---

## 9) POST /api/videos/{videoId}/playUrls
列出所有已 ready 的 transcode 的 playlist presigned URLs（用于管理 UI /手动清晰度选择）

认证
- 可选

响应（data = List）
每项示例：
```json
{ "representationId":"720p", "bitrate":1800000, "resolution":"1280x720", "url":"https://.../stream_1.m3u8?X-Amz-..." }
```

---

## 10) POST /api/videos/reportMetrics
前端上报播放质量（ABR metrics）

认证
- 需要登录（可但不强制）

请求（AbrReportRequest）
```json
{
  "sessionUuid":"sess-abc",
  "videoId":42,
  "playDurationMs":600000,
  "avgBitrate":1200000,
  "startupDelayMs":800,
  "rebufferCount":2,
  "totalRebufferMs":2000,
  "extra":"{...}"
}
```

响应
- Result.success("ok")

后端行为
- 写入 abr_sessions 与 abr_session_metrics 表（用于分析、优化 ABR 策略）

---

# Chat 模块接口（好友 / 群 / 消息 / 会话）

共同说明
- 所有与用户相关的接口必须依赖 `@CurrentUser` 注入当前用户 id（通过 token）。
- DTO 命名位于 `com.anime.common.dto.chat.*`，通常采用请求和响应对。

---

## 好友相关

### POST /api/chat/friends/list
获取当前用户的好友列表

请求
- 空 body 或 ListFriendsRequest（当前实现忽略分页）

响应（ListFriendsResponse）
```json
{
  "friends":[
    {"id":201,"username":"alice","email":"a@x.com","avatarUrl":"https://..."},
    ...
  ]
}
```

---

### POST /api/chat/friends/add
立即添加好友（直接建立双向关系）

请求（AddFriendRequest）
```json
{ "friendUid": 201 }
```

响应（AddFriendResponse）
```json
{ "id": 201, "username":"alice", "email":"a@x.com", "avatarUrl":"https://..." }
```

说明
- 若已经是好友，接口保持幂等（不会重复插入）。

---

### POST /api/chat/friends/remove
删除好友（双向删除）

请求（RemoveFriendRequest）
```json
{ "friendId": 201 }
```

响应
- Result.success(true)

---

### POST /api/chat/friends/request/send
发送好友请求（创建 pending friend_requests）

请求（SendFriendRequestRequest）
```json
{ "toUserId": 201, "message": "Hi, let's connect" }
```

响应（SendFriendRequestResponse）
```json
{ "requestId": 345, "status": "pending" }
```

行为
- 若已有 pending 请求则返回 existing request id 与 status pending。
- 若已经是好友则返回 status `already_friends`。

---

### POST /api/chat/friends/request/list
列出当前用户收到的 pending 好友请求

请求
- 空 body

响应（ListFriendRequestsResponse）
```json
{
  "items":[
    { "requestId":345, "fromUserId":102, "fromUsername":"bob", "fromAvatarUrl":"https://...", "message":"Hi","createdAt":"2026-01-06 16:00:00" }
  ]
}
```

注意
- 需要认证；若未认证应返回 401。

---

### POST /api/chat/friends/request/respond
处理好友请求：accept / reject

请求（HandleFriendRequestRequest）
```json
{ "requestId": 345, "action": "accept" }  // action 可为 "accept" 或 "reject"
```

响应
- Result.success("ok")

行为
- 当 action == accept：在 user_friends 表中建立双向关系（若不存在），并把 friend_requests.status = 'accepted'。
- 当 action == reject：设置 friend_requests.status = 'rejected'。

权限
- 仅请求接收方（to_user_id）可处理该请求；实现需检查 currentUser。

---

### POST /api/chat/friends/search
按用户名精确匹配（严格匹配）

请求（SearchUserRequest）
```json
{ "username": "alice" }
```

响应（SearchUserResponse）
```json
{
  "items":[
    { "id":201, "username":"alice", "avatarUrl":"https://...", "personalSignature":"Hi there" }
  ]
}
```

说明
- 若未找到结果返回空 items 列表。

---

## 群聊相关（ChatGroupController）

### POST /api/chat/groups/create
创建群聊（当前用户为群主）

请求（CreateGroupRequest）
```json
{
  "name":"Study Group",
  "memberUuids":[201,202],   // 不包含自己
  "description":"Group for study"
}
```

响应（CreateGroupResponse）
```json
{ "groupId": 55, "name":"Study Group", "ownerId": 123, "createdAt":"2026-01-06 16:00:00" }
```

说明
- 后端会插入群表与群成员表（群主 + 成员）。权限：需登录。

---

### POST /api/chat/groups/list
列出当前用户所在的群聊

请求
- 空或 ListGroupsRequest

响应（ListGroupsResponse）
```json
{ "groups": [ { "groupId":55, "name":"Study Group", "lastMessage":"...", "unreadCount":2 }, ... ] }
```

---

### POST /api/chat/groups/members
列出指定群的成员（需传 groupId）

请求（ListGroupMembersRequest）
```json
{ "groupId": 55 }
```

响应（ListGroupMembersResponse）
```json
{
  "members":[ { "userId":201, "username":"alice", "avatarUrl":"https://..." }, ... ]
}
```

权限
- 仅群成员或受限访问（由实现判定）可查看。

---

## 消息历史查询（ChatMessageController）

> 实时发送/接收使用 WebSocket，以下接口仅用于历史消息查询与标记/删除操作。

### POST /api/chat/messages/private
获取与某好友的私聊历史（分页参数可选）

请求（ListPrivateMessagesRequest）
```json
{ "peerUserId": 201, "limit":50, "beforeMessageId": null }
```

响应（ListPrivateMessagesResponse）
```json
{
  "messages":[
    { "id":987, "fromUserId":201, "toUserId":123, "content":"Hi", "type":"text", "createdAt":"2026-01-06 15:00:00" },
    ...
  ]
}
```

---

### POST /api/chat/messages/group
获取某群的群聊历史

请求（ListGroupMessagesRequest）
```json
{ "groupId": 55, "limit":50, "beforeMessageId":null }
```

响应（ListGroupMessagesResponse）
```json
{ "messages":[ /* group messages */ ] }
```

权限
- 仅群成员可查看。

---

### POST /api/chat/messages/private/markRead
把与某好友的私聊消息标记为已读（当前用户为接收方）

请求（MarkPrivateMessagesReadRequest）
```json
{ "peerUserId":201, "upToMessageId":987 } // 标记到该消息为止
```

响应
```json
{ "readCount": 12 }
```

---

### POST /api/chat/messages/group/markRead
把某群的消息全部标记为已读（当前用户为接收方）

请求（MarkGroupMessagesReadRequest）
```json
{ "groupId":55, "upToMessageId":12345 }
```

响应
```json
{ "readCount": 20 }
```

---

### POST /api/chat/messages/delete
单向删除（对当前用户隐藏）某条消息

请求（DeleteMessageRequest）
```json
{ "messageId": 987 }
```

响应（DeleteMessageResponse）
```json
{ "deleted": true }
```

说明
- 仅把消息对当前用户隐藏，不影响其他用户或群成员。

---

## 会话列表（ChatSessionController）

### POST /api/chat/sessions/list
返回当前用户所有会话（单聊 + 群聊），用于聊天左侧列表展示。

请求
- 空或 ListSessionsRequest

响应（ListSessionsResponse）
```json
{
  "sessions":[
    { "sessionId":"user-201", "type":"private", "peerUserId":201, "lastMessage":"Hi", "lastAt":"2026-01-06 16:00:00", "unreadCount":2 },
    { "sessionId":"group-55", "type":"group", "groupId":55, "lastMessage":"Meeting at 8", "lastAt":"2026-01-06 15:50:00", "unreadCount":0 }
  ]
}
```

---

# 常见示例调用（带 token）

1. 登录并抓取 New-Access-Token header
```bash
curl -v -X POST https://api.example.com/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"alice","password":"pwd"}' -k
# 检查 response header New-Access-Token
```

2. 列出收到的好友请求（带 token）
```bash
curl -X POST https://api.example.com/api/chat/friends/request/list \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{}'
```

3. 播放视频（hls.js）使用后端 proxy master:
```
const master = `${baseUrl}/api/videos/${videoId}/hls/playlist?name=master.m3u8&expiry=300`;
hls.loadSource(master);
```

---

# 运维与实现注意要点（回顾）
- HLS 多码率与私有存储：
  - 推荐后端代理 playlist 并把 segment 替换为 presigned URLs，前端直接请求 segments。
  - Ensure MinIO/AWS S3 CORS allows GET from browser origin to presigned URLs.
- 转码：
  - 转码 worker 会探测源分辨率并只生成不高于源的标准档位（1080/720/360/240），避免“伪升分”。
  - 在无音频源的情况下，worker 会自动跳过音频映射以避免 ffmpeg 错误。
- 点赞：
  - 使用 video_likes.active 标记点赞状态，首次插入记录，后续 update active（比 insert/delete 更可追溯、更高效）。
  - videos.like_count 通过 atomic DB increment/decrement 保持缓存，与 like 表保持一致性（注意高并发）。
- 鉴权：
  - 建议 playlist proxy endpoint 允许匿名访问（便于播放），因为实际分片受 presigned URL 保护；若需强认证可在 hls.js xhrSetup 注入 Authorization header。
- 日志与调试：
  - 转码过程务必输出 ffmpeg/ffprobe stdout/stderr 到日志以便定位错误。
  - 在调试阶段可保留转码生成的临时文件，方便人工检查 m3u8 与 segment 名称的一致性。
