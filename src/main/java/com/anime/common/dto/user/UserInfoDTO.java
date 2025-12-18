package com.anime.common.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应 DTO
 */
@Data
@Schema(description = "用户信息")
@NoArgsConstructor
public class UserInfoDTO {
    @Schema(description = "用户ID", example = "42")
    private String id;

    @Schema(description = "用户名", example = "alice")
    private String username;

    @Schema(description = "头像路径或 CDN URL")
    private String userAvatarUrl;

    @Schema(description = "个性签名")
    private String personalSignature;
}