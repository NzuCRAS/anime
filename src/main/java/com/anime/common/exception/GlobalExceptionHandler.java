package com.anime.common.exception;

import com.anime.common.enums.ResultCode;
import com.anime.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 统一异常处理器：把常见异常转为统一的 Result 响应格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<String>> handleBadRequest(IllegalArgumentException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Result<String>> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(401).body(Result.fail(ResultCode.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<String>> handleAny(Exception ex) {
        // 记录日志若需要（这里不直接打印 token 等敏感信息）
        return ResponseEntity.status(500).body(Result.fail(ResultCode.SYSTEM_ERROR, "服务器内部错误"));
    }
}