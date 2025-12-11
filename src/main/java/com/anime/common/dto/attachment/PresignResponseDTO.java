package com.anime.common.dto.attachment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 返回给前端的 presign 结果 DTO
 * 有时间限制，会过期
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresignResponseDTO {
    /**
     * attachment数据库里的主键
     */
    private Long attachmentId;

    /**
     * 文件在文件系统中的存储路径，唯一标识文件在bucket中的位置
     */
    private String storageKey;

    /**
     * 给前端直接使用的文件存储地址，直接put即可
     */
    private String putUrl;

    /**
     * 简化为 Map<String, String> 便于前端直接设置 header。
     * 注意：部分签名需要多个同名 header（rare），此处使用逗号拼接。
     * 服务器在生成 presigned PUT 时同时返回给前端的一组 HTTP header（key→value）
     * 在签名时这些 header 可能被包含到签名里，上传时必须完全匹配签名时的值，否则对象存储会拒绝请求
     * 用来告诉前端在 PUT 上传时需要带哪些 header，且这些 header 很可能被签名绑定
     */
    private Map<String, String> putHeaders;

    /**
     * 用于预览或者直接下载的url
     */
    private String getUrl;
}