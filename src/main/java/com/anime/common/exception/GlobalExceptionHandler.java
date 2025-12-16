package com.anime.common.exception;

import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

/**
 * 统一异常处理器：把常见异常转为统一的 Result 响应格式
 *
 * 注意：
 * - 仅保留一个针对 Exception 的通用处理方法，避免与其他 @ExceptionHandler 冲突。
 * - 对安全相关异常（AuthenticationException / AccessDeniedException）做明确返回。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<String>> handleBadRequest(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Bad request {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<String>> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        log.warn("Authentication failure for {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(401).body(Result.fail(ResultCode.UNAUTHORIZED, "Unauthorized: " + ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<String>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied for {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(403).body(Result.fail(ResultCode.FORBIDDEN, "Forbidden: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<String>> handleAllExceptions(HttpServletRequest req, Exception ex) {
        // 记录完整堆栈，便于调试（开发时非常有用）
        log.error("Unhandled exception for request {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);

        // 返回统一的 Result 格式（保留原有风格）
        return ResponseEntity.status(500).body(Result.fail(ResultCode.SYSTEM_ERROR, "服务器内部错误: " + ex.getMessage()));
    }
}