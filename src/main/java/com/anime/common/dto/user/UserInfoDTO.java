package com.anime.common.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户信息响应 DTO
 */
@Data
@Schema(description = "用户信息")
public class UserInfoDTO {
    @Schema(description = "用户ID", example = "42")
    private String id;

    @Schema(description = "用户名", example = "alice")
    private String username;

    @Schema(description = "邮箱", example = "alice@example.com")
    private String email;

    @Schema(description = "头像路径或 CDN URL")
    private String avatarPath;

    @Schema(description = "个性签名")
    private String personalSignature;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "上次登录时间")
    private LocalDateTime lastLogin;
}