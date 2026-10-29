package com.projlife.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CRM crm_sales_funnel（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("crm_sales_funnel")
public class CrmSalesFunnel extends BaseEntity {

    /** 商机 id */
    private Long opportunityId;

    /** 阶段（OPPORTUNITY/QUOTE/CONTRACT/DELIVERY） */
    private String stage;

    /** 进入时间 */
    private LocalDateTime enterTime;

    /** 离开时间 */
    private LocalDateTime exitTime;

    /** 停留天数 */
    private Integer stayDays;

    /** 金额 */
    private BigDecimal amount;

    /** 概率 */
    private BigDecimal probability;

    public Long getOpportunityId() {
        return opportunityId;
    }

    public void setOpportunityId(Long opportunityId) {
        this.opportunityId = opportunityId;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public LocalDateTime getEnterTime() {
        return enterTime;
    }

    public void setEnterTime(LocalDateTime enterTime) {
        this.enterTime = enterTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public Integer getStayDays() {
        return stayDays;
    }

    public void setStayDays(Integer stayDays) {
        this.stayDays = stayDays;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getProbability() {
        return probability;
    }

    public void setProbability(BigDecimal probability) {
        this.probability = probability;
    }

}