package com.projlife.common.exception;

import com.projlife.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常兜底（16 册 §4.4）：错误码 → HTTP 状态映射；系统错误只返回 traceId 不暴露堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException ex) {
        HttpStatus status = toStatus(ex.getCode());
        if (status.is5xxServerError()) {
            log.error("业务异常 code={}", ex.getCode(), ex);
        }
        return ResponseEntity.status(status)
                .body(ApiResponse.of(ex.getCode(), ex.getMessage(), ex.getData().isEmpty() ? null : ex.getData()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiResponse.of(ErrorCode.PARAM_INVALID, "参数校验失败", errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.of(ErrorCode.PARAM_INVALID, ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleOther(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.of(ErrorCode.SYSTEM_ERROR, "系统繁忙，请联系管理员并提供 traceId", null));
    }

    private HttpStatus toStatus(int code) {
        switch (code) {
            case ErrorCode.PARAM_INVALID:
                return HttpStatus.BAD_REQUEST;
            case ErrorCode.UNAUTHORIZED:
                return HttpStatus.UNAUTHORIZED;
            case ErrorCode.FORBIDDEN:
            case ErrorCode.DATA_SCOPE:
                return HttpStatus.FORBIDDEN;
            case ErrorCode.NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case ErrorCode.CONFLICT:
                return HttpStatus.CONFLICT;
            case ErrorCode.BLOCKED:
                return HttpStatus.UNPROCESSABLE_ENTITY;
            case ErrorCode.TOO_MANY:
                return HttpStatus.TOO_MANY_REQUESTS;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
