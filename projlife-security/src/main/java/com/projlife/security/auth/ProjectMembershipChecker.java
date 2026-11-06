package com.projlife.security.auth;

/**
 * 项目在册成员查询 SPI（SPRINT-C PROJECT 档落地）。
 * projlife-security 不依赖 module-project（避免循环），由 module-project 提供实现 Bean
 * （查 prj_member：project_id + user_id + is_active=1，与列表层 AnnotationDataPermissionHandler
 * 的 PROJECT 档 SQL 子查询同口径）。
 * 无实现 Bean 时（最小装配/单测直连），DataScopeGuard PROJECT 档退回备案放行。
 */
@FunctionalInterface
public interface ProjectMembershipChecker {

    /** 当前用户是否为项目在册成员 */
    boolean isActiveMember(Long projectId, Long userId);
}
