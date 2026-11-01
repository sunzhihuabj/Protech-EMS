package com.projlife.org.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projlife.common.context.LoginUser;
import com.projlife.common.context.UserContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审计切面（S0-6）：@Audited 方法成功返回后异步写 sys_operation_log。
 * after_json=首个入参快照（密码字段由 DTO @JsonIgnore 排除）；before_json 由业务显式补记。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    /** 用户操作日志专用 Logger → 路由到 operation-{date}.log（logback-spring.xml） */
    private static final Logger operationLog = LoggerFactory.getLogger("OPERATION");
    private static final Pattern ARG_REF = Pattern.compile("\\{(\\d)}");

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public AuditAspect(OperationLogService operationLogService, ObjectMapper objectMapper) {
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            try {
                record(joinPoint, audited, cost);
            } catch (Exception ex) {
                log.warn("审计记录写入失败 action={}", audited.action(), ex);
            }
        }
    }

    private void record(ProceedingJoinPoint joinPoint, Audited audited, long cost) {
        LoginUser user = UserContextHolder.get();
        Object[] args = joinPoint.getArgs();
        String afterJson = safeJson(args.length > 0 ? args[0] : null);
        operationLogService.record(
                user == null ? null : user.getUserId(),
                user == null ? "system" : user.getRealName(),
                audited.module(), audited.action(), audited.targetType(),
                resolveTargetId(audited.targetId(), args),
                null, afterJson, currentIp(), (int) cost);

        // 双写：同时写用户操作日志文件（operation-{date}.log）
        operationLog.info("ACTION={} | MODULE={} | USER={}/{} | TARGET={}/{} | COST={}ms | RESULT={}",
                audited.action(), audited.module(),
                user == null ? "system" : user.getUserCode(),
                user == null ? "system" : user.getRealName(),
                audited.targetType(), resolveTargetId(audited.targetId(), args),
                cost, "OK");
    }

    private String resolveTargetId(String expression, Object[] args) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        Matcher matcher = ARG_REF.matcher(expression);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            Object value = index < args.length ? args[index] : null;
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(value == null ? "" : String.valueOf(idOf(value))));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private Object idOf(Object value) {
        if (value instanceof com.projlife.common.entity.BaseEntity) {
            return ((com.projlife.common.entity.BaseEntity) value).getId();
        }
        return value;
    }

    private String safeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            return forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        }
        return null;
    }
}
