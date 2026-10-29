package com.projlife.security.auth;

/**
 * 内置权限：全员默认拥有、不需要授权的权限码（03 册 §6A.2）。
 */
public final class BuiltinPermissions {

    private BuiltinPermissions() {
    }

    /** 切换个人主题（全员默认拥有） */
    public static final String PROFILE_THEME_SWITCH = "profile:theme:switch";

    private static final String[] ALL = {PROFILE_THEME_SWITCH};

    public static boolean isBuiltin(String permCode) {
        for (String p : ALL) {
            if (p.equals(permCode)) {
                return true;
            }
        }
        return false;
    }

    public static String[] all() {
        return ALL.clone();
    }
}
