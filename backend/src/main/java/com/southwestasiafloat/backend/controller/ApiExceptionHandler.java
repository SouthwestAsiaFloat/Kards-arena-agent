package com.southwestasiafloat.backend.controller;

/**
 * 统一异常处理器，将后端异常转换为前端可识别的响应。
 */

import com.southwestasiafloat.backend.application.analysis.support.ServiceBusyException;
import com.southwestasiafloat.backend.application.ratelimit.RateLimitExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<Map<String, String>> handleBusy(ServiceBusyException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException ex) {
        long retryAfterSeconds = Math.max(1, (long) Math.ceil(ex.getRetryAfter().toMillis() / 1000.0d));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(Map.of(
                        "error", ex.getMessage(),
                        "retryAfterSeconds", Long.toString(retryAfterSeconds)
                ));
    }
}
