package com.anime.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisAccessTokenBlacklistService implements AccessTokenBlacklistService {

    private final StringRedisTemplate redis;
    private static final String KEY_PREFIX = "blacklist:access_token:jti:";

    public RedisAccessTokenBlacklistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void blacklist(String jti, long ttlMillis) {
        if (jti == null || jti.isBlank()) return;
        String key = KEY_PREFIX + jti;
        // value not important, store timestamp
        redis.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), Duration.ofMillis(Math.max(1000, ttlMillis)));
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) return false;
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}