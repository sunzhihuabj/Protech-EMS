package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-THD thd_supplier_account（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("thd_supplier_account")
public class ThdSupplierAccount extends BaseEntity {

    /** 供应商 */
    private Long supplierId;

    /** 门户账号 */
    private Long userId;

    /** BUSINESS/FINANCE */
    private String accountType;

    /** PENDING/ACTIVE/FROZEN/EXPIRED */
    private String status;

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}