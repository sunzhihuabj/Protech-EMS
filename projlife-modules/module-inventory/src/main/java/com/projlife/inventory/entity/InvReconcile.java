package com.projlife.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-INV inv_reconcile（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("inv_reconcile")
public class InvReconcile extends BaseEntity {

    /** 对账日 */
    private LocalDate reconcileDate;

    /** 法人 */
    private Long legalEntityId;

    /** 本地事务笔数 */
    private Integer localTxnCount;

    /** T+ 流水笔数 */
    private Integer tPlusTxnCount;

    /** 差异数 */
    private Integer diffCount;

    /** 差异金额 */
    private BigDecimal diffAmount;

    /** E-02 */
    private String status;

    public LocalDate getReconcileDate() {
        return reconcileDate;
    }

    public void setReconcileDate(LocalDate reconcileDate) {
        this.reconcileDate = reconcileDate;
    }

    public Long getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(Long legalEntityId) {
        this.legalEntityId = legalEntityId;
    }

    public Integer getLocalTxnCount() {
        return localTxnCount;
    }

    public void setLocalTxnCount(Integer localTxnCount) {
        this.localTxnCount = localTxnCount;
    }

    public Integer getTPlusTxnCount() {
        return tPlusTxnCount;
    }

    public void setTPlusTxnCount(Integer tPlusTxnCount) {
        this.tPlusTxnCount = tPlusTxnCount;
    }

    public Integer getDiffCount() {
        return diffCount;
    }

    public void setDiffCount(Integer diffCount) {
        this.diffCount = diffCount;
    }

    public BigDecimal getDiffAmount() {
        return diffAmount;
    }

    public void setDiffAmount(BigDecimal diffAmount) {
        this.diffAmount = diffAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}