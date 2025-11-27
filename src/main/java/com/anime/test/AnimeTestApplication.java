package com.anime.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot. autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors. UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@SpringBootApplication(
        scanBasePackages = "com.anime",
        exclude = {
                org.springframework.boot.autoconfigure. jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.redis. RedisAutoConfiguration.class,
                com.alibaba.cloud.nacos.NacosConfigAutoConfiguration. class,
                com.alibaba.cloud.nacos.discovery. NacosDiscoveryAutoConfiguration.class,
                SecurityAutoConfiguration.class
        }
)
public class AnimeTestApplication {

    public static void main(String[] args) {
        System.setProperty("spring.profiles. active", "test");
        System.setProperty("spring.cloud. nacos.config.enabled", "false");
        System.setProperty("spring.cloud.nacos. discovery.enabled", "false");
        System.setProperty("spring. docker.compose.enabled", "false");

        SpringApplication.run(AnimeTestApplication.class, args);
        System.out.println("=================================");
        System.out.println("🎌 Anime Community Test API Started!");
        System.out.println("📍 Test API: http://localhost:8080/api/test/hello");
        System.out.println("🏓 Ping API: http://localhost:8080/api/test/ping");
        System.out.println("=================================");
    }

    // 在启动类中直接配置CORS
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许的源
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));  // 测试时允许所有源
        // 或者具体指定
        // configuration.setAllowedOrigins(Arrays.asList(
        //     "http://localhost:3000",
        //     "http://localhost:5173",
        //     "http://localhost:8080"
        // ));

        // 允许的方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 允许的头部
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // 允许凭证
        configuration.setAllowCredentials(true);

        // 预检请求缓存时间
        configuration. setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }
}