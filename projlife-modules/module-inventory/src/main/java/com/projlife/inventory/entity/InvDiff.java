package com.projlife.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-INV inv_diff（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("inv_diff")
public class InvDiff extends VersionedEntity {

    /** DF-{YYYYMM}-{4位} */
    private String diffNo;

    /** 盘点单 */
    private Long countId;

    /** 物料 */
    private Long materialId;

    /** 货位 */
    private Long locationId;

    /** 账面 */
    private BigDecimal bookQty;

    /** 实盘 */
    private BigDecimal actualQty;

    /** 差异 */
    private BigDecimal diffQty;

    /** 差异金额 */
    private BigDecimal diffAmount;

    /** E-37（无归因不调账） */
    private String cause;

    /** L1 库房主管/L2 计划/L3 财务双签 */
    private String approvalLevel;

    /** 审批人 */
    private Long approvedBy;

    /** 审批时间 */
    private LocalDateTime approvedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 关闭天数 */
    private Integer closeDays;

    public String getDiffNo() {
        return diffNo;
    }

    public void setDiffNo(String diffNo) {
        this.diffNo = diffNo;
    }

    public Long getCountId() {
        return countId;
    }

    public void setCountId(Long countId) {
        this.countId = countId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public BigDecimal getBookQty() {
        return bookQty;
    }

    public void setBookQty(BigDecimal bookQty) {
        this.bookQty = bookQty;
    }

    public BigDecimal getActualQty() {
        return actualQty;
    }

    public void setActualQty(BigDecimal actualQty) {
        this.actualQty = actualQty;
    }

    public BigDecimal getDiffQty() {
        return diffQty;
    }

    public void setDiffQty(BigDecimal diffQty) {
        this.diffQty = diffQty;
    }

    public BigDecimal getDiffAmount() {
        return diffAmount;
    }

    public void setDiffAmount(BigDecimal diffAmount) {
        this.diffAmount = diffAmount;
    }

    public String getCause() {
        return cause;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    public String getApprovalLevel() {
        return approvalLevel;
    }

    public void setApprovalLevel(String approvalLevel) {
        this.approvalLevel = approvalLevel;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public Integer getCloseDays() {
        return closeDays;
    }

    public void setCloseDays(Integer closeDays) {
        this.closeDays = closeDays;
    }

}