package com.projlife.org.service.audit;

import com.projlife.common.context.UserContextHolder;
import com.projlife.common.web.TraceIdFilter;
import com.projlife.org.entity.SysOperationLog;
import com.projlife.org.mapper.SysOperationLogMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 操作日志写入（审计表禁 UPDATE/DELETE：仓库无更新方法，DB 层另限权，03 册 §七）。
 * 同步写入：登录上下文只在请求线程有效，异步化会丢失操作人。
 */
@Service
public class OperationLogService {

    private final SysOperationLogMapper logMapper;

    public OperationLogService(SysOperationLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    public void record(Long userId, String userName, String module, String action,
                       String targetType, String targetId, String beforeJson, String afterJson,
                       String ip, Integer costMs) {
        SysOperationLog log = new SysOperationLog();
        log.setUserId(userId);
        log.setUserName(userName);
        log.setModule(module);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setBeforeJson(beforeJson);
        log.setAfterJson(afterJson);
        log.setIp(ip);
        log.setTraceId(MDC.get(TraceIdFilter.MDC_KEY));
        log.setCostMs(costMs);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    /** 精确 before/after 场景由业务 Service 显式调用 */
    public void recordManual(String module, String action, String targetType, String targetId,
                             String beforeJson, String afterJson) {
        com.projlife.common.context.LoginUser user = UserContextHolder.get();
        record(user == null ? null : user.getUserId(),
                user == null ? "system" : user.getRealName(),
                module, action, targetType, targetId, beforeJson, afterJson, null, null);
    }
}
