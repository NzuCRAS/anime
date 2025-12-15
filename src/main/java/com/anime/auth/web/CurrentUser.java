package com.anime.auth.web;

import java.lang.annotation.*;

/**
 * 注入当前用户 id（从 SecurityContext 中获取 principal）。
 * 用法：
 *   public ResponseEntity<?> foo(@CurrentUser Long userId) { ... }
 *
 * 当请求未认证时，参数会被解析为 null（若你希望直接返回 401 可在 resolver 中抛异常）。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}