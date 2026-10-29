package com.projlife.api;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 主应用（00 册 C-02：模块化单体，聚合 14 个业务模块中的 S0 已交付部分）。
 * 端口 8080；Flyway 迁移唯一入口（其他进程共享库表，不重复迁移）。
 */
@SpringBootApplication(scanBasePackages = "com.projlife")
@MapperScan(basePackages = "com.projlife", annotationClass = Mapper.class)
@EnableAsync
public class ProjLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjLifeApplication.class, args);
    }
}
