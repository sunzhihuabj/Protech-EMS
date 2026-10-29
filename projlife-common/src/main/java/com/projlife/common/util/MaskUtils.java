package com.projlife.common.util;

/**
 * 脱敏工具（BR-06 前置）：导出与日志中敏感字段统一处理。
 * 规则：手机号 138****5678；证件号保留前 4 后 4；姓名保留姓。
 */
public final class MaskUtils {

    private MaskUtils() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    public static String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    public static String maskAmount() {
        return "null";
    }
}
