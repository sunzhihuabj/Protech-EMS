package com.projlife.common.spi;

/**
 * 钉钉 userid 解析 SPI（SPRINT-INT2）：通知适配器（module-integration）按 sys_user.id 解析钉钉 userid。
 * 实现在 module-org（sys_dingtalk_binding 绑定表）。查无绑定返回 null（调用方走机器人兜底）。
 */
public interface DingtalkUserResolver {

    /** sys_user.id → 钉钉 userid；未绑定返回 null */
    String resolveBySysUserId(Long sysUserId);

    /** sys_user.id → 钉钉 unionId（待办 API 用）；未绑定返回 null */
    String resolveUnionIdBySysUserId(Long sysUserId);
}
