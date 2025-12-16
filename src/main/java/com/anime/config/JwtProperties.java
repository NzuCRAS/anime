package com.anime.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
@RefreshScope
public class JwtProperties {
    // 密钥
    private String secret;

    // 签发者
    private String issuer = "anime";  // 默认值

    // 访问令牌配置
    private AccessToken accessToken;

    // 刷新令牌配置(RefreshToken是从AccessToken获取的)
    private RefreshToken refreshToken;

    /**
     * 当写入 refresh cookie 时是否设置 Secure 属性（生产环境建议 true）。
     * 在开发本地使用 http 时可设置为 false，但在启用 HTTPS 后应设为 true。
     */
    private boolean cookieSecure = true;

    /**
     * SameSite 值（"None" / "Lax" / "Strict"），跨域 AJAX 刷新场景需要 "None" 并且 cookieSecure=true。
     */
    private String cookieSameSite = "Lax";

    @Data
    public static class AccessToken {
        // 到期时间,单位:毫秒(可以理解为这个token持续多长时间)
        private Long expiration;

        // HTTP 请求头名称
        private String header = "Authorization";

        // Token 前缀
        private String prefix = "Bearer ";
    }

    @Data
    public static class RefreshToken {
        // 到期时间,单位:毫秒
        private Long expiration;

        // Redis 中存储刷新令牌的键前缀
        private String redisKeyPrefix = "refresh_token:";
    }
}