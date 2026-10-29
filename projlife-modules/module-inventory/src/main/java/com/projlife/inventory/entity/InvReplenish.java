package com.projlife.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-INV inv_replenish（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("inv_replenish")
public class InvReplenish extends VersionedEntity {

    /** RP-{YYYYMMDD}-{3位} */
    private String replenishNo;

    /** 执行项目（BR-03） */
    private Long projectId;

    /** 项目档案 */
    private Long mdProjectId;

    /** E-40 八类 */
    private String reason;

    /** 紧急度 */
    private String urgency;

    /** 申请人 */
    private Long applicantId;

    /** 金额 */
    private BigDecimal amount;

    /** 审批链 */
    private String approvalPath;

    /** 需补签（安全件先发后签） */
    private Integer needBackfill;

    /** 补签期限（出库+24h） */
    private LocalDateTime backfillDeadline;

    /** 补签时间 */
    private LocalDateTime backfilledAt;

    /** 待审批/已审批/已出库/已补签/已关闭/已锁定 */
    private String status;

    /** 超时锁定申请人（BR-05） */
    private Integer applicantLocked;

    public String getReplenishNo() {
        return replenishNo;
    }

    public void setReplenishNo(String replenishNo) {
        this.replenishNo = replenishNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getMdProjectId() {
        return mdProjectId;
    }

    public void setMdProjectId(Long mdProjectId) {
        this.mdProjectId = mdProjectId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public Long getApplicantId() {
        return applicantId;
    }

    public void setApplicantId(Long applicantId) {
        this.applicantId = applicantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getApprovalPath() {
        return approvalPath;
    }

    public void setApprovalPath(String approvalPath) {
        this.approvalPath = approvalPath;
    }

    public Integer getNeedBackfill() {
        return needBackfill;
    }

    public void setNeedBackfill(Integer needBackfill) {
        this.needBackfill = needBackfill;
    }

    public LocalDateTime getBackfillDeadline() {
        return backfillDeadline;
    }

    public void setBackfillDeadline(LocalDateTime backfillDeadline) {
        this.backfillDeadline = backfillDeadline;
    }

    public LocalDateTime getBackfilledAt() {
        return backfilledAt;
    }

    public void setBackfilledAt(LocalDateTime backfilledAt) {
        this.backfilledAt = backfilledAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getApplicantLocked() {
        return applicantLocked;
    }

    public void setApplicantLocked(Integer applicantLocked) {
        this.applicantLocked = applicantLocked;
    }

}