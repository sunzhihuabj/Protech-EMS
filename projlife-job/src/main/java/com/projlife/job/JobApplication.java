package com.projlife.job;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 定时任务进程（00 册 C-02）：提醒/升级/指标预计算/对账/预警。
 * ShedLock（JDBC 行锁）保证多副本不重复执行；任务默认关闭（projlife.job.enabled=false）。
 */
@SpringBootApplication(scanBasePackages = "com.projlife.job")
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class JobApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }

    @Bean
    public LockProvider lockProvider(JdbcTemplate jdbcTemplate) {
        return new JdbcTemplateLockProvider(
                new JdbcTemplateLockProvider.Configuration.Builder()
                        .withJdbcTemplate(jdbcTemplate)
                        .usingDbTime()
                        .build());
    }
}
