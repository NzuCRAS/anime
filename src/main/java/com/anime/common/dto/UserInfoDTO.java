package com.anime.common.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户信息响应 DTO
 */
@Data
public class UserInfoDTO {
    private String id;
    private String username;
    private String email;
    private String avatarPath;
    private String personalSignature;
    private LocalDateTime createdTime;
    private LocalDateTime lastLogin;
}
