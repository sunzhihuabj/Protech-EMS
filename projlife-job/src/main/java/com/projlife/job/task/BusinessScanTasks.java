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

    /** T+ 每日对账的法人主体（多法人生产经 PROJLIFE_JOB_LEGAL_ENTITY_ID 注入，SPRINT-UID-R18） */
    @Value("${projlife.job.legal-entity-id:1}")
    private Long legalEntityId;

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
        int n = invoker.post("/api/internal/jobs/kb/metric-fetch");
        if (enabled) {
            log.info("指标自动取数完成：{} 项快照", n);
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

    // ---------- SPRINT-UID-R18：孤儿 job 入口补挂（此前端点无任何调用方） ----------

    @Scheduled(cron = "0 15 * * * ?")
    @SchedulerLock(name = "replenish-lock-overdue", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void replenishLockOverdue() {
        int n = invoker.post("/api/v1/inv/replenish/lock-overdue");
        if (enabled) {
            log.info("补货逾期锁定（BR-05）：{} 单超 25h 未回填被锁定", n);
        }
    }

    @Scheduled(cron = "0 0 4 * * ?")
    @SchedulerLock(name = "inv-daily-reconcile", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void inventoryDailyReconcile() {
        String yesterday = java.time.LocalDate.now().minusDays(1).toString();
        int n = invoker.post("/api/v1/inv/reconcile",
                "{\"date\":\"" + yesterday + "\",\"legalEntityId\":" + legalEntityId + "}");
        if (enabled) {
            log.info("T+ 每日对账（{}）完成：{} 条差异", yesterday, n);
        }
    }

    @Scheduled(cron = "0 30 8 * * ?")
    @SchedulerLock(name = "contract-dispatch-remind", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void contractDispatchRemind() {
        int n = invoker.post("/api/internal/jobs/contract/remind-unread");
        if (enabled) {
            log.info("合同交底未读再提醒：{} 条", n);
        }
    }

    @Scheduled(cron = "0 40 8 * * ?")
    @SchedulerLock(name = "contract-payment-overdue-remind", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void contractPaymentOverdueRemind() {
        int n = invoker.post("/api/internal/jobs/contract/payment-overdue-remind");
        if (enabled) {
            log.info("逾期收款计划提醒：{} 行", n);
        }
    }
}
