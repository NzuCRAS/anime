package com.anime.config;

import com.anime.auth.filter.JwtAuthenticationFilter;
import com.anime.common.exception.RestAccessDeniedHandler;
import com.anime.common.exception.RestAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 明确放行 OpenAPI / Swagger 相关路径与静态资源
 */
@Configuration
public class SecurityConfig {

    private final ObjectProvider<JwtAuthenticationFilter> jwtFilterProvider;
    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;

    public SecurityConfig(ObjectProvider<JwtAuthenticationFilter> jwtFilterProvider,
                          ObjectMapper objectMapper,
                          JwtProperties jwtProperties) {
        this.jwtFilterProvider = jwtFilterProvider;
        this.objectMapper = objectMapper;
        this.jwtProperties = jwtProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 如果处于开发模式（devMode=true），放行所有请求（仅用于本地开发调试）
        if (jwtProperties != null && jwtProperties.isDevMode()) {
            // 注意：仍然保留 CORS/CSRF/异常处理的基本配置，但将所有请求 permitAll()
            http
                    .cors(Customizer.withDefaults())
                    .csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(eh -> eh
                            .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                            .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper))
                    )
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll()
                    )
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
            // 不注册 jwt 过滤器
            return http.build();
        }

        // 非开发模式：原有安全配置
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper))
                )
                .authorizeHttpRequests(auth -> auth
                        // 公开的 API
                        .requestMatchers(HttpMethod.POST, "/api/user/login", "/api/user/register", "/api/auth/refresh", "/api/user/logout")
                        .permitAll()

                        // swagger / openapi / static resources - 明确放行
                        .requestMatchers(
                                "/webjars/**",
                                "/favicon.ico",
                                "/favicon-*",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/assets/**",
                                "/static/**",
                                "/public/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/swagger-ui-dist/**",
                                "/swagger-resources/**",
                                "/api/test/whoami"
                        ).permitAll()

                        // 其它公开资源
                        .requestMatchers("/api/user/ping", "/api/attachments/**").permitAll()

                        // 其余都需要认证
                        .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        JwtAuthenticationFilter jwtFilter = jwtFilterProvider.getIfAvailable();
        if (jwtFilter != null) {
            // 把 jwt 过滤器放到 UsernamePasswordAuthenticationFilter 之前
            http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}