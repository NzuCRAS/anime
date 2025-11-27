package com.anime.auth.service;

import com.anime.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework. stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 生成AccessToken
     */
    public String generateAccessToken(Long userId, String userName) {
        return generateAccessToken(userId, userName, null);
    }

    /**
     * 生成AccessToken（支持自定义过期时间）
     */
    public String generateAccessToken(Long userId, String userName, Long customExpiration) {
        try {
            // 参数验证
            if (userId == null || userName == null || userName.trim().isEmpty()) {
                throw new IllegalArgumentException("用户ID和用户名不能为空");
            }

            // 使用自定义过期时间或默认配置
            long expiration = customExpiration != null ? customExpiration :
                    jwtProperties.getAccessToken(). getExpiration();
            String secret = jwtProperties.getSecret();
            String issuer = jwtProperties.getIssuer();

            // 构建Claims
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", userId);
            claims.put("username", userName.trim());
            claims.put("tokenType", "access");

            // 从配置中获取密钥
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());

            String token = Jwts.builder()
                    .claims(claims)
                    .issuer(issuer)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + expiration))
                    .id(UUID.randomUUID().toString())
                    .signWith(key)
                    .compact();

            log.debug("为用户 {} 生成AccessToken成功", userName);
            return token;

        } catch (Exception e) {
            log.error("为用户 {} 生成AccessToken失败: {}", userName, e.getMessage());
            throw new RuntimeException("生成AccessToken失败", e);
        }
    }

    /**
     * 解析Token获取Claims
     */
    public Claims parseToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret(). getBytes());

            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    . parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("解析Token失败: {}", e.getMessage());
            throw new RuntimeException("Token解析失败", e);
        }
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            log.debug("Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从Token中提取用户ID
     */
    public Long extractUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从Token中提取用户名
     */
    public String extractUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 生成RefreshToken
     */
    public String generateRefreshToken(Long userId) {
        // RefreshToken通常有更长的有效期
    }

    /**
     * 刷新AccessToken
     */
    public String refreshAccessToken(String refreshToken) {
        // 验证RefreshToken并生成新的AccessToken
    }
}