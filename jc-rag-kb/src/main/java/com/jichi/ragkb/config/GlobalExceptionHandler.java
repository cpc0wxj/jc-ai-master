package com.jichi.ragkb.config;

import com.jichi.ragkb.dto.ApiResponse;
import com.jichi.ragkb.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 统一捕获业务异常和系统异常，返回标准化 API 响应
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException e) {
        log.warn("GlobalExceptionHandler.handleBizException code={},message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode())
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("GlobalExceptionHandler.handleException message={}", e.getMessage(), e);
        return ResponseEntity.status(500)
                .body(ApiResponse.error(500, "系统内部错误，请稍后重试"));
    }
}