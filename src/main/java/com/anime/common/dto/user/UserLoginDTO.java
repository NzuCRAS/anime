package com.anime.common.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户登录请求 DTO
 */
@Data
@Schema(description = "登录请求 DTO")
public class UserLoginDTO {

    @Schema(description = "用户名或邮箱", example = "test_name")
    private String usernameOrEmail;

    @Schema(description = "密码（明文，使用 HTTPS）", example = "password123")
    private String password;
}