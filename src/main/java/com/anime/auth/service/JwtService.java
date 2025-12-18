package com.anime.auth.service;

import com.anime.config.JwtProperties;
import com.anime.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 负责生成/解析/验证 JWT（access + refresh）
 */
@Slf4j
@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    public JwtService(JwtProperties jwtProperties,
                      RefreshTokenService refreshTokenService,
                      UserService userService) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
    }

    public long getAccessExpirationMillis() {
        return jwtProperties.getAccessToken().getExpiration();
    }

    public long getRefreshExpirationMillis() {
        return jwtProperties.getRefreshToken().getExpiration();
    }

    public String generateAccessToken(Long userId, String userName) {
        return generateAccessToken(userId, userName, null);
    }

    public String generateAccessToken(Long userId, String userName, Long customExpirationMillis) {
        if (userId == null || userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("userId and userName must not be null/empty");
        }

        long exp = customExpirationMillis != null ? customExpirationMillis : getAccessExpirationMillis();

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", userName.trim());
        claims.put("tokenType", "access");

        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + exp))
                .setId(UUID.randomUUID().toString())
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        long exp = getRefreshExpirationMillis();

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("tokenType", "refresh");

        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + exp))
                .setId(UUID.randomUUID().toString())
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并返回 Claims（使用 jjwt parser）
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("parseToken failed: {}", e.getMessage());
            throw new RuntimeException("Token parse failed", e);
        }
    }

    /**
     * 验证 token 的有效性（签名 + 过期）
     */
    public boolean validateToken(String token) {
        try {
            Claims c = parseToken(token);
            return c.getExpiration() != null && !c.getExpiration().before(new Date());
        } catch (Exception e) {
            log.debug("validateToken: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            Claims c = parseToken(token);
            return "access".equals(c.get("tokenType", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims c = parseToken(token);
            return "refresh".equals(c.get("tokenType", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 token 中提取 userId
     */
    public Long extractUserId(String token) {
        Claims c = parseToken(token);
        Number n = c.get("userId", Number.class);
        return n == null ? null : n.longValue();
    }

    /**
     * 从 token 中提取 jti
     */
    public String extractJti(String token) {
        Claims c = parseToken(token);
        return c.getId();
    }

    /**
     * 从 token 中提取 username
     */
    public String extractUsername(String token) {
        Claims c = parseToken(token);
        return c.get("username", String.class);
    }

    /**
     * 计算 token 剩余毫秒数（若无法解析或无 exp 返回 <=0）
     */
    public long getRemainingMillis(String token) {
        try {
            Claims c = parseToken(token);
            Date exp = c.getExpiration();
            if (exp == null) return 0L;
            return Math.max(0L, exp.getTime() - System.currentTimeMillis());
        } catch (Exception e) {
            log.debug("getRemainingMillis failed: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 生成新的 access+refresh pair（不做 Redis 旋转操作）
     */
    public TokenPair createTokenPair(Long userId, String username) {
        String newAccess = generateAccessToken(userId, username);
        String newRefresh = generateRefreshToken(userId);
        return new TokenPair(newAccess, newRefresh);
    }

    /**
     * 使用 refreshToken 刷新并返回新的 TokenPair（包含 access + refresh）
     */
    public TokenPair refreshAccessToken(String refreshToken) {
        if (refreshToken == null) {
            throw new IllegalArgumentException("refreshToken is null");
        }

        // 1. 基础验证
        if (!validateToken(refreshToken) || !isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        // 2. 检查 jti 在 Redis 中是否存在（未被撤销）
        String oldJti = extractJti(refreshToken);
        if (!refreshTokenService.validateRefreshToken(oldJti)) {
            throw new IllegalArgumentException("Refresh token is not valid (not found or revoked)");
        }

        // 3. 获取 userId 与 username
        Long userId = extractUserId(refreshToken);
        if (userId == null) {
            throw new IllegalArgumentException("Invalid refresh token payload: missing userId");
        }
        String username = userService.getUsernameById(userId);
        if (username == null) {
            throw new IllegalArgumentException("User not found for id: " + userId);
        }

        // 4. 生成新对 token
        TokenPair newPair = createTokenPair(userId, username);
        String newJti = extractJti(newPair.refreshToken);

        // 5. 在 Redis 中旋转（删除旧 jti，写入新 jti）
        boolean rotated = refreshTokenService.rotateRefreshTokenAtomic(oldJti, newJti, userId, getRefreshExpirationMillis());
        if (!rotated) {
            throw new IllegalArgumentException("Refresh token rotation failed (may be replay or revoked)");
        }
        log.debug("refreshAccessToken: rotated refresh jti {} -> {}", oldJti, newJti);
        return newPair;
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}