package com.projlife.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.projlife.common.context.UserContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis-Plus 全局配置：分页、乐观锁、审计字段自动填充（16 册 §4.4）。
 * 其他模块（如安全层的数据范围拦截器）以 InnerInterceptor Bean 声明，在此统一收集。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(List<InnerInterceptor> innerInterceptors) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        for (InnerInterceptor inner : innerInterceptors) {
            interceptor.addInnerInterceptor(inner);
        }
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /** created_by/updated_by 从登录上下文填充；无登录态（定时任务/系统动作）填 system */
    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                String operator = currentUserCode();
                this.strictInsertFill(metaObject, "createdBy", String.class, operator);
                this.strictInsertFill(metaObject, "updatedBy", String.class, operator);
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedBy", String.class, currentUserCode());
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }

    /**
     * created_by/updated_by 存工号（SELF 数据范围以工号匹配，03 册 §1.1）；
     * 无人登录时填 system（定时任务/系统动作）。
     */
    private String currentUserCode() {
        com.projlife.common.context.LoginUser user = UserContextHolder.get();
        return user == null || user.getUserCode() == null ? "system" : user.getUserCode();
    }
}
