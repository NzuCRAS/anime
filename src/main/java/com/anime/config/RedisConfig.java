package com.anime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisTemplate 配置：使用 String 序列化器，避免序列化不一致导致 key 查询失败
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 默认配置，使用 application.properties 中 spring.redis.* 覆盖
        return new LettuceConnectionFactory();
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory factory) {
        RedisTemplate<String, String> t = new RedisTemplate<>();
        t.setConnectionFactory(factory);

        // 使用 String 序列化，避免默认 JDK 序列化导致的 key/lookup 问题
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        t.setKeySerializer(stringSerializer);
        t.setValueSerializer(stringSerializer);
        t.setHashKeySerializer(stringSerializer);
        t.setHashValueSerializer(stringSerializer);
        t.afterPropertiesSet();
        return t;
    }
}