package com.projlife.security.datascope;

import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.projlife.security.auth.DeptTreeProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * 数据范围拦截器注册：以 InnerInterceptor Bean 的形式被公共层 MybatisPlusInterceptor 收集。
 * DeptTreeProvider 请求期惰性解析，避免与 sqlSessionFactory 形成循环依赖。
 */
@Configuration
public class DataScopeInterceptorConfig {

    @Bean
    @Order(0)
    public DataPermissionInterceptor dataPermissionInterceptor(ObjectProvider<DeptTreeProvider> deptTreeProvider) {
        return new DataPermissionInterceptor(new AnnotationDataPermissionHandler(deptTreeProvider::getObject));
    }
}
