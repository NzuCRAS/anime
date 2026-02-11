package com.anime.common.dto.user;

import lombok.Data;

/**
 * DTO: 更新用户个人签名
 * 前端请求示例:
 * { "personalSignature": "这是我的新签名" }
 */
@Data
public class PersonalSignatureDTO {
    private String personalSignature;
}