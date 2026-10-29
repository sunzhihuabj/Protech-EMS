package com.projlife.common.enums;

/**
 * 数据范围五级（03 册 §1.1，E-35）。
 * 只控制"能看到哪些行"，不控制字段脱敏；两者独立判断。
 */
public enum DataScope {

    /** 本人：created_by = :me OR assignee_id = :me */
    SELF,
    /** 本部门 */
    DEPT,
    /** 本部门及下级 */
    DEPT_SUB,
    /** 项目成员：project_id IN (SELECT project_id FROM prj_member WHERE user_id=:me AND is_active=1) */
    PROJECT,
    /** 全局 */
    ALL;

    /** 取两档中更宽的一档（角色并集时使用） */
    public static DataScope wider(DataScope a, DataScope b) {
        if (a == ALL || b == ALL) {
            return ALL;
        }
        if (a == PROJECT || b == PROJECT) {
            return PROJECT;
        }
        if (a == DEPT_SUB || b == DEPT_SUB) {
            return DEPT_SUB;
        }
        if (a == DEPT || b == DEPT) {
            return DEPT;
        }
        return SELF;
    }

    public static DataScope of(String code) {
        if (code == null || code.isEmpty()) {
            return SELF;
        }
        return valueOf(code);
    }
}
