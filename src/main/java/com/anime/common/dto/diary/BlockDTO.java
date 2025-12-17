package com.anime.common.dto.diary;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "日记的单个 Block，对应页面上的一段内容（文本/图片/嵌入等）")
public class BlockDTO {
    @Schema(description = "客户端临时 id 或已有数据库 id（更新时传）", example = "123")
    private Long blockId;

    @Schema(description = "块类型，例如 'text' / 'image' / 'embed'", example = "image", required = true)
    private String type;

    @Schema(description = "文本内容（text 类型时使用）", example = "这是一个文本块")
    private String content;

    @Schema(description = "引用的 attachment id（image 类型时使用）", example = "456")
    private Long attachmentId;

    @Schema(description = "位置（后端会按前端顺序重新分配），从1开始", example = "1")
    private Integer position;

    @Schema(description = "metadata JSON 字符串（例如 caption/alt/layout）", example = "{\"caption\":\"示例\"}")
    private String metadata;
}