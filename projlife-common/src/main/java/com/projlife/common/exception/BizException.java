package com.projlife.common.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * 业务异常：携带错误码与附加 data（如 blockCode/suggestion/missingPerm）。
 */
public class BizException extends RuntimeException {

    private final int code;
    private final Map<String, Object> data = new HashMap<>();

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ErrorCode.FORBIDDEN, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }

    /** 硬卡点：422 + blockCode + 引导语（16 册 §4.3） */
    public static BizException blocked(String blockCode, String message, String suggestion) {
        BizException ex = new BizException(ErrorCode.BLOCKED, message);
        ex.data.put(ErrorCode.BLOCK_CODE, blockCode);
        if (suggestion != null) {
            ex.data.put(ErrorCode.SUGGESTION, suggestion);
        }
        return ex;
    }

    public BizException with(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    public int getCode() {
        return code;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
