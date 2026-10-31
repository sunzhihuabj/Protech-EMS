package com.projlife.job.task;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 业务扫描任务装配（S24 轨道三）：把 S8-S17 已交付的 job 入口真正挂上调度。
 * 各端点自带硬卡点与幂等；多副本经 ShedLock 保证不重复。
 *
 * <ul>
 *   <li>02:00 九板红灯 3 天升级（UC-KB-03，FR-KB-07）</li>
 *   <li>02:30 档案借阅逾期扫描（OVERDUE，V4-02 M13）</li>
 *   <li>03:00 第三方人员授权到期扫描（EXPIRED，V4-05 M11）</li>
   *   <li>03:30 库龄自动跑批识别呆滞（CANDIDATE，S12 遗留 → R5 落地）</li>
 *   <li>每 10 分钟 Andon 分级升级（工位→班长→主任，FR-MES-30）</li>
 * </ul>
 */
@Component
public class BusinessScanTasks {

    private static final Logger log = LoggerFactory.getLogger(BusinessScanTasks.class);

    private final InternalJobInvoker invoker;

    @Value("${projlife.job.enabled:false}")
    private boolean enabled;

    public BusinessScanTasks(InternalJobInvoker invoker) {
        this.invoker = invoker;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(name = "kb-red-escalate", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void escalateRedCards() {
        int n = invoker.post("/api/v1/kb/cards/escalate");
        if (enabled) {
            log.info("红灯升级完成：{} 张", n);
        }
    }

    @Scheduled(cron = "0 30 2 * * ?")
    @SchedulerLock(name = "arc-overdue-scan", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void arcOverdueScan() {
        int n = invoker.post("/api/v1/arc/borrows/overdue-scan");
        if (enabled) {
            log.info("档案逾期扫描完成：{} 条置 OVERDUE", n);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "thd-expire-scan", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void thdExpireScan() {
        int n = invoker.post("/api/internal/jobs/thd/expire-scan");
        if (enabled) {
            log.info("人员授权到期扫描完成：{} 条置 EXPIRED", n);
        }
    }

    @Scheduled(cron = "0 */10 * * * ?")
    @SchedulerLock(name = "andon-escalate", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void andonEscalate() {
        int n = invoker.post("/api/v1/mes/andon/escalate");
        if (enabled) {
            log.info("Andon 升级扫描完成：{} 条", n);
        }
    }

    @Scheduled(cron = "0 30 3 * * ?")
    @SchedulerLock(name = "inv-age-scan", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void inventoryAgeScan() {
        int n = invoker.post("/api/internal/jobs/inv/age-scan");
        if (enabled) {
            log.info("库龄扫描完成：{} 条批次进呆滞候选", n);
        }
    }

    @Scheduled(cron = "0 15 2 * * ?")
    @SchedulerLock(name = "metric-auto-fetch", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void metricAutoFetch() {
        try {
            var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:mysql://localhost:3306/projlife?useSSL=false", "root", "");
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
            Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mes_production_order WHERE deleted = 0", Long.class);
            Long done = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mes_production_order WHERE deleted = 0 AND status = 'COMPLETED'", Long.class);
            if (total != null && total > 0) {
                String period = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                double rate = Math.round(done * 10000.0 / total) / 100.0;
                String body = "{\"period\":\"" + period + "\",\"value\":\"" + rate + "\"}";
                invoker.post("/api/v1/kb/metrics/S17.MO.DELIVERY/snapshots", body);
                if (enabled) log.info("指标自动取数：交付率 {}%", rate);
            }
        } catch (Exception e) {
            log.warn("指标自动取数失败：{}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 4 * * ?")
    @SchedulerLock(name = "arc-appraisal-scan", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void arcAppraisalScan() {
        int n = invoker.post("/api/v1/arc/appraisal-scan");
        if (enabled) {
            log.info("档案鉴定提醒扫描：{} 份距到期 ≤365 天", n);
        }
    }
}
