package com.anime.common.dto.chat.whiteboard;

import lombok.Data;

/**
 * 客户端发送：加入某个白板（boardId 可为空用于创建）
 */
@Data
public class WhiteboardJoinRequest {
    // 若为空，则服务端会创建一个新的 boardId（create）
    private String boardId;
    // 参与方（由服务端从 session 注入时通常不需客户端传）
    private Long targetUserId; // 当要创建一个新白板并指定对方时可传
}