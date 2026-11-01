package com.projlife.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 业务流程日志工具（FR 日志分类）：
 * 通过专用 Logger "BUSINESS" 路由到 logs/business/business-{date}.log。
 *
 * 用法：BusinessLogHelper.event("商机创建", "OPP-202610-0001", "R04", "business");
 */
public final class BusinessLogHelper {

    private static final Logger log = LoggerFactory.getLogger("BUSINESS");

    private BusinessLogHelper() {}

    /** 记录业务事件 */
    public static void event(String event, String bizNo, String operator, String module) {
        log.info("EVENT={} | BIZ_NO={} | OPERATOR={} | MODULE={}", event, bizNo, operator, module);
    }

    /** 记录业务状态变更 */
    public static void transition(String entity, String bizNo, String from, String to, String operator) {
        log.info("TRANSITION={} | BIZ_NO={} | {} -> {} | OPERATOR={}", entity, bizNo, from, to, operator);
    }

    /** 记录审批事件 */
    public static void approval(String approvalNo, Long nodeId, String action, String approver, String comment) {
        log.info("APPROVAL={} | NODE={} | ACTION={} | APPROVER={} | COMMENT={}", approvalNo, nodeId, action, approver, comment);
    }

    /** 记录异常/预警 */
    public static void warn(String module, String message) {
        log.warn("ALERT={} | {}", module, message);
    }
}
