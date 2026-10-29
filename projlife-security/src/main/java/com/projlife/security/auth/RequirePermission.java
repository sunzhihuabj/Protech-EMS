package com.projlife.security.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级权限校验注解（03 册 §1.2）。
 * 用在 Controller 方法上；未命中返回 403 + data.missingPerm。前端 v-permission 只控显隐，不作为唯一防线。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限码，格式 模块:操作[:对象] */
    String value();
}
