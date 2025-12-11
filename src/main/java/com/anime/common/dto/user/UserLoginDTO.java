package com.anime.common.dto.user;

import lombok.Data;

/**
 * 用户登录请求 DTO
 */
@Data
public class UserLoginDTO {

    private String usernameOrEmail;

    private String password;
}