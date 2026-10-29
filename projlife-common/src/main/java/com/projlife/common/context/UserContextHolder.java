package com.projlife.common.context;

/**
 * 登录上下文（ThreadLocal）。审计字段自动填充、权限校验、数据范围改写统一从此读取。
 */
public final class UserContextHolder {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static Long currentUserId() {
        LoginUser u = CONTEXT.get();
        return u == null || u.getUserId() == null ? null : u.getUserId();
    }

    public static String currentUserCode() {
        LoginUser u = CONTEXT.get();
        return u == null ? "system" : (u.getUserCode() == null ? "unknown" : u.getUserCode());
    }

    public static String currentUserName() {
        LoginUser u = CONTEXT.get();
        return u == null ? "system" : (u.getRealName() == null ? u.getUsername() : u.getRealName());
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
