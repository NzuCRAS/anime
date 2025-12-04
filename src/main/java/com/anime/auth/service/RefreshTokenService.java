package com.anime.auth.service;

import com.anime.config.JwtProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;


/**
 * Redis 中管理 refresh token 的 jti（或 token hash）
 * 提供基于 Lua 的原子 rotate 方法，防止并发重放问题
 */
@Service
public class RefreshTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String KEY_PREFIX = new JwtProperties.RefreshToken().getRedisKeyPrefix(); // 从JwtProperties获取前缀,统一管理

    // Lua 脚本内容可放在资源文件或直接内嵌字符串（示例内嵌）
    private static final String ROTATE_LUA = ""
            + "local oldKey = KEYS[1]\n"
            + "local newKey = KEYS[2]\n"
            + "local userId = ARGV[1]\n"
            + "local ttl = tonumber(ARGV[2])\n"
            + "if redis.call('EXISTS', oldKey) == 1 then\n"
            + "  redis.call('DEL', oldKey)\n"
            + "  redis.call('SET', newKey, userId)\n"
            + "  redis.call('PEXPIRE', newKey, ttl)\n"
            + "  return 1\n"
            + "else\n"
            + "  return 0\n"
            + "end";

    public RefreshTokenService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeRefreshToken(String jti, Long userId, long ttlMillis) {
        String key = KEY_PREFIX + jti;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), Duration.ofMillis(ttlMillis));
    }

    public boolean validateRefreshToken(String jti) {
        if (jti == null) return false;
        String key = KEY_PREFIX + jti;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 原子化旋转：删除旧 jti，写入新 jti（Lua 脚本保证原子性）
     * 返回 true 表示旋转成功；false 表示旧 jti 不存在（可能已被使用或撤销）
     * jti指jwt的唯一标识符
     * 用于防止刷新令牌的重放攻击
     */
    public boolean rotateRefreshTokenAtomic(String oldJti, String newJti, Long userId, long ttlMillis) {
        if (oldJti == null || newJti == null || userId == null) return false;

        String oldKey = KEY_PREFIX + oldJti;
        String newKey = KEY_PREFIX + newJti;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ROTATE_LUA, Long.class);
        // KEYS = [oldKey, newKey], ARGV = [userId, ttlMillis]
        Long result = redisTemplate.execute(script, Arrays.asList(oldKey, newKey), String.valueOf(userId), String.valueOf(ttlMillis));
        return result != null && result == 1L; // 这里写的result非空检查是为了防止NPE,execute有可能返回null
    }

    public void revokeRefreshToken(String jti) {
        if (jti == null) return;
        redisTemplate.delete(KEY_PREFIX + jti);
    }
}