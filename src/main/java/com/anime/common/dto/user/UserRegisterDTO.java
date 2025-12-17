package com.anime.common.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户注册请求 DTO
 */
@Data
@Schema(description = "注册请求 DTO")
public class UserRegisterDTO {
    @Schema(description = "用户名", example = "alice")
    private String username;

    @Schema(description = "密码", example = "password123")
    private String password;

    @Schema(description = "确认密码（需与 password 相同）", example = "password123")
    private String confirmPassword;

    @Schema(description = "邮箱", example = "alice@example.com")
    private String email;
}