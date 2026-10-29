package com.projlife.common.exception;

/**
 * 全局错误码表（16 册 §4.3）。
 * HTTP 语义映射在 GlobalExceptionHandler 完成。
 */
public interface ErrorCode {

    int OK = 0;

    /** 400 参数校验失败 */
    int PARAM_INVALID = 1001;
    /** 401 未登录/会话过期 */
    int UNAUTHORIZED = 2001;
    /** 403 无权限 */
    int FORBIDDEN = 2002;
    /** 403 数据范围外 */
    int DATA_SCOPE = 2003;
    /** 404 资源不存在 */
    int NOT_FOUND = 3001;
    /** 409 并发冲突（乐观锁） */
    int CONFLICT = 4001;
    /** 422 业务规则阻断（硬卡点统一 422，data.blockCode=BR-0x） */
    int BLOCKED = 5001;
    /** 429 频率限制 */
    int TOO_MANY = 6001;
    /** 500 系统错误 */
    int SYSTEM_ERROR = 9001;

    /** 硬卡点 blockCode 的 data key */
    String BLOCK_CODE = "blockCode";
    /** 硬卡点引导语 data key */
    String SUGGESTION = "suggestion";
}
