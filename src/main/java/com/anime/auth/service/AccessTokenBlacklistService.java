package com.anime.auth.service;

public interface AccessTokenBlacklistService {
    /**
     * 把 access token 的 jti 加入黑名单，并设置过期时长（毫秒）
     */
    void blacklist(String jti, long ttlMillis);

    /**
     * 检查 jti 是否在黑名单
     */
    boolean isBlacklisted(String jti);
}