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
 * Spring Security 配置：注册 JwtAuthenticationFilter 到过滤链
 *
 * JwtAuthenticationFilter 使用 ObjectProvider 延迟注入，避免循环依赖。
 * 密码编码器的 bean 已移到独立的 PasswordConfig。
 */
@Configuration
public class SecurityConfig {

    private final ObjectProvider<JwtAuthenticationFilter> jwtFilterProvider;
    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectProvider<JwtAuthenticationFilter> jwtFilterProvider, ObjectMapper objectMapper) {
        this.jwtFilterProvider = jwtFilterProvider;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/user/login", "/api/user/register", "/api/auth/refresh")
                        .permitAll()
                        .requestMatchers("/public/**", "/static/**",  "/api/user/ping","/api/attachments/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        JwtAuthenticationFilter jwtFilter = jwtFilterProvider.getIfAvailable();
        if (jwtFilter != null) {
            http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}