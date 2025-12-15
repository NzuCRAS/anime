package com.anime.common.dto.diary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * BlockDTO - 用于前端 -> 后端的数据传输对象
 * 前端在新建 block 时可以给一个临时 id（例如负数或客户端本地 uuid hash）以便前端区分。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockDTO {
    private Long blockId;           // 前端临时 id 或已有 DB id
    private String type;
    private String content;
    private Long attachmentId;
    private Integer position;  // 前端传的 position 会被后端重新覆盖为 1..N
    private String metadata;
}