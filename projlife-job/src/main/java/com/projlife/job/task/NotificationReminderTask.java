package com.projlife.job.task;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 提醒任务骨架（S0-12 配套）：每日 08:00 汇总昨日未读通知再提醒。
 * 后续切片按 16 册 §五 各自追加：BR-02 冻结检查（S1）、Andon 升级（S5）、对账（S4）、红灯升级（S8）。
 */
@Component
public class NotificationReminderTask {

    private static final Logger log = LoggerFactory.getLogger(NotificationReminderTask.class);

    @Value("${projlife.job.enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "0 0 8 * * ?")
    @SchedulerLock(name = "notification-reminder", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void remind() {
        if (!enabled) {
            return;
        }
        log.info("通知提醒任务开始（骨架，正式逻辑随切片交付）");
        // S0 仅验证调度与锁机制；具体汇总推送在后续切片实现
    }
}
