package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_after_sales（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_after_sales")
public class EngAfterSales extends VersionedEntity {

    /** WO-{YYYYMMDD}-{3位} UK */
    private String orderNo;

    /** 项目档案（BR-ENG-08 必挂） */
    private Long mdProjectId;

    /** 机组 */
    private Long unitId;

    /** 故障类型 */
    private String faultType;

    /** 紧急度 */
    private String urgency;

    /** 质保内 */
    private Integer warrantyFlag;

    /** 报修时间 */
    private LocalDateTime reportedAt;

    /** 响应时间 */
    private LocalDateTime respondedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 费用 */
    private BigDecimal costAmount;

    /** 处理人 */
    private Long handlerId;

    /** TO_DISPATCH/DISPATCHED/DOING/TO_ACCEPT/CLOSED */
    private String status;

    /** 方案 */
    private String solution;

    /** 客户确认 */
    private Integer customerConfirm;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getMdProjectId() {
        return mdProjectId;
    }

    public void setMdProjectId(Long mdProjectId) {
        this.mdProjectId = mdProjectId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public String getFaultType() {
        return faultType;
    }

    public void setFaultType(String faultType) {
        this.faultType = faultType;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public Integer getWarrantyFlag() {
        return warrantyFlag;
    }

    public void setWarrantyFlag(Integer warrantyFlag) {
        this.warrantyFlag = warrantyFlag;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(LocalDateTime reportedAt) {
        this.reportedAt = reportedAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public BigDecimal getCostAmount() {
        return costAmount;
    }

    public void setCostAmount(BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSolution() {
        return solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }

    public Integer getCustomerConfirm() {
        return customerConfirm;
    }

    public void setCustomerConfirm(Integer customerConfirm) {
        this.customerConfirm = customerConfirm;
    }

}