package com.projlife.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CRM crm_customer_profile（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("crm_customer_profile")
public class CrmCustomerProfile extends BaseEntity {

    /** 客户主体 id */
    private Long customerId;

    /** 累计合同额 */
    private BigDecimal totalContractAmount;

    /** 累计应收 */
    private BigDecimal totalReceivable;

    /** 累计回款 */
    private BigDecimal totalReceived;

    /** 投诉次数 */
    private Integer complaintCount;

    /** 满意度 */
    private BigDecimal satisfactionScore;

    /** 最近交易日期 */
    private LocalDate lastTxnDate;

    /** R 分（1-3） */
    private Integer rfmR;

    /** F 分（1-3） */
    private Integer rfmF;

    /** M 分（1-3） */
    private Integer rfmM;

    /** 画像扩展 */
    private String profileJson;

    /** 快照日期 */
    private LocalDate snapshotDate;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getTotalContractAmount() {
        return totalContractAmount;
    }

    public void setTotalContractAmount(BigDecimal totalContractAmount) {
        this.totalContractAmount = totalContractAmount;
    }

    public BigDecimal getTotalReceivable() {
        return totalReceivable;
    }

    public void setTotalReceivable(BigDecimal totalReceivable) {
        this.totalReceivable = totalReceivable;
    }

    public BigDecimal getTotalReceived() {
        return totalReceived;
    }

    public void setTotalReceived(BigDecimal totalReceived) {
        this.totalReceived = totalReceived;
    }

    public Integer getComplaintCount() {
        return complaintCount;
    }

    public void setComplaintCount(Integer complaintCount) {
        this.complaintCount = complaintCount;
    }

    public BigDecimal getSatisfactionScore() {
        return satisfactionScore;
    }

    public void setSatisfactionScore(BigDecimal satisfactionScore) {
        this.satisfactionScore = satisfactionScore;
    }

    public LocalDate getLastTxnDate() {
        return lastTxnDate;
    }

    public void setLastTxnDate(LocalDate lastTxnDate) {
        this.lastTxnDate = lastTxnDate;
    }

    public Integer getRfmR() {
        return rfmR;
    }

    public void setRfmR(Integer rfmR) {
        this.rfmR = rfmR;
    }

    public Integer getRfmF() {
        return rfmF;
    }

    public void setRfmF(Integer rfmF) {
        this.rfmF = rfmF;
    }

    public Integer getRfmM() {
        return rfmM;
    }

    public void setRfmM(Integer rfmM) {
        this.rfmM = rfmM;
    }

    public String getProfileJson() {
        return profileJson;
    }

    public void setProfileJson(String profileJson) {
        this.profileJson = profileJson;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

}