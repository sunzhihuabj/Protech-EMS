package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-COST int_reconcile_diff（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("int_reconcile_diff")
public class IntReconcileDiff extends BaseEntity {

    /** 适配器 id */
    private Long adapterId;

    /** 本地金额 */
    private BigDecimal localAmount;

    /** 远端金额 */
    private BigDecimal remoteAmount;

    /** 差额 */
    private BigDecimal diffAmount;

    /** 业务日期 */
    private LocalDate bizDate;

    /** 已解决 */
    private Integer resolved;

    /** 解决人 */
    private Long resolvedBy;

    /** 解决时间 */
    private LocalDateTime resolvedAt;

    /** 备注 */
    private String remark;

    public Long getAdapterId() {
        return adapterId;
    }

    public void setAdapterId(Long adapterId) {
        this.adapterId = adapterId;
    }

    public BigDecimal getLocalAmount() {
        return localAmount;
    }

    public void setLocalAmount(BigDecimal localAmount) {
        this.localAmount = localAmount;
    }

    public BigDecimal getRemoteAmount() {
        return remoteAmount;
    }

    public void setRemoteAmount(BigDecimal remoteAmount) {
        this.remoteAmount = remoteAmount;
    }

    public BigDecimal getDiffAmount() {
        return diffAmount;
    }

    public void setDiffAmount(BigDecimal diffAmount) {
        this.diffAmount = diffAmount;
    }

    public LocalDate getBizDate() {
        return bizDate;
    }

    public void setBizDate(LocalDate bizDate) {
        this.bizDate = bizDate;
    }

    public Integer getResolved() {
        return resolved;
    }

    public void setResolved(Integer resolved) {
        this.resolved = resolved;
    }

    public Long getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(Long resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}