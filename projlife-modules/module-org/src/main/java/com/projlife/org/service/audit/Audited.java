package com.projlife.org.service.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计注解（03 册 §七）：写操作自动记录 谁/何时/目标/参数快照/IP/traceId。
 * targetId 支持 {n} 形式引用第 n 个入参（对象则取其 toString/id）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 模块标识，如 org/contract/plm */
    String module();

    /** 动作标识，如 create_user/freeze_user */
    String action();

    /** 目标类型（表名或业务对象） */
    String targetType() default "";

    /** 目标标识表达式：{n} 引用第 n 个入参 */
    String targetId() default "";
}
