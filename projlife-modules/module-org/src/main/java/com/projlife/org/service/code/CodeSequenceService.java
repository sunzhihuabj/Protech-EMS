package com.projlife.org.service.code;

import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编码引擎（S0-7，FR 依据 02 册 §4.17 + §五）。
 *
 * 并发唯一机制：同一事务内「INSERT .. ON DUPLICATE KEY UPDATE 自增（行锁）」→ 同事务 SELECT 读回
 * 自己的增量。后到事务在 UPDATE 行锁上等待，提交后读到自己的值；允许空档不补号。
 * 禁止任何业务代码绕过本服务拼编码。
 */
@Service
public class CodeSequenceService {

    private static final Logger log = LoggerFactory.getLogger(CodeSequenceService.class);

    private final JdbcTemplate jdbcTemplate;

    public CodeSequenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 取下一个编码。REQUIRES_NEW：取号提交不受外层业务回滚影响（产生的空档符合"允许空档"规则）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(CodeTypes.CodeRequest request) {
        String scopeKey = request.getScopeKey();
        String period = periodOf(scopeKey);
        jdbcTemplate.update(
                "INSERT INTO sys_code_sequence (scope_key, period, current_value, step, prefix, created_by, created_at, updated_by, updated_at) "
                        + "VALUES (?, ?, 1, 1, ?, 'system', NOW(), 'system', NOW()) "
                        + "ON DUPLICATE KEY UPDATE current_value = current_value + step, updated_at = NOW()",
                scopeKey, period, request.getDisplayPrefix());
        Long value = jdbcTemplate.queryForObject(
                "SELECT current_value FROM sys_code_sequence WHERE scope_key = ?", Long.class, scopeKey);
        if (value == null) {
            log.error("编码取号失败：序列行不存在 scopeKey={}", scopeKey);
            throw BizException.of(ErrorCode.SYSTEM_ERROR, "编码取号失败");
        }
        return request.getDisplayPrefix() + String.format("%0" + request.getDigits() + "d", value);
    }

    /** scope_key 最后一段即为周期（年度/月/日/GLOBAL） */
    private String periodOf(String scopeKey) {
        int idx = scopeKey.lastIndexOf(':');
        return idx < 0 ? scopeKey : scopeKey.substring(idx + 1);
    }

    /**
     * 编码预览（FR-CT-18 / FR-MDM-44）：返回当前值（不占号）；序列未初始化返回 0。
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public long peek(CodeTypes.CodeRequest request) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT current_value FROM sys_code_sequence WHERE scope_key = ?",
                Long.class, request.getScopeKey());
        return value == null ? 0 : value;
    }

    /** 列出全部编码序列（R10 Stub 页数据源） */
    public java.util.List<java.util.Map<String, Object>> listAll() {
        return jdbcTemplate.queryForList(
            "SELECT code_key, prefix, current_value, updated_at FROM sys_code_sequence ORDER BY code_key LIMIT 200");
    }
}
