package com.projlife.security.datascope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据范围规则声明（03 册 §1.1）。标注在 Mapper 方法上，声明本查询用于范围判断的列。
 * 未标注的查询不做数据范围改写（如字典、参数等全局只读数据）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScopeRule {

    /** 部门列名（DEPT/DEPT_SUB 用） */
    String deptColumn() default "dept_id";

    /** 项目列名（PROJECT 用） */
    String projectColumn() default "project_id";

    /** 创建人列名（SELF 用；库中存工号） */
    String userColumn() default "created_by";

    /** SELF 按用户 ID 匹配的列名（如接收人 receiver_id）；非空时优先于 userColumn */
    String selfIdColumn() default "";
}
