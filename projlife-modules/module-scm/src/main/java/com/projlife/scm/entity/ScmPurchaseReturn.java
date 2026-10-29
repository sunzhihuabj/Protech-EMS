package com.projlife.scm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-SCM scm_purchase_return（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("scm_purchase_return")
public class ScmPurchaseReturn extends BaseEntity {

    /** 退货单号 */
    private String returnNo;

    /** 原 PO */
    private Long poId;

    /** 行 id */
    private Long itemId;

    /** 退货数量 */
    private BigDecimal qty;

    /** 原因 */
    private String reason;

    /** 退货日期 */
    private LocalDate returnDate;

    /** E-02 */
    private String status;

    /** T+ 冲减凭证 */
    private String tPlusRef;

    public String getReturnNo() {
        return returnNo;
    }

    public void setReturnNo(String returnNo) {
        this.returnNo = returnNo;
    }

    public Long getPoId() {
        return poId;
    }

    public void setPoId(Long poId) {
        this.poId = poId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTPlusRef() {
        return tPlusRef;
    }

    public void setTPlusRef(String tPlusRef) {
        this.tPlusRef = tPlusRef;
    }

}