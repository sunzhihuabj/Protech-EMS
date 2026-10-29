package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_legal_entity（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_legal_entity")
public class MdLegalEntity extends BaseEntity {

    /** 主体码 PRT/TLD */
    private String entityCode;

    /** 法人名称 */
    private String entityName;

    /** 税号 */
    private String taxNo;

    /** 注册地址 */
    private String address;

    /** 开户信息 */
    private String bankInfo;

    /** 开票抬头 */
    private String invoiceHeader;

    public String getEntityCode() {
        return entityCode;
    }

    public void setEntityCode(String entityCode) {
        this.entityCode = entityCode;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getTaxNo() {
        return taxNo;
    }

    public void setTaxNo(String taxNo) {
        this.taxNo = taxNo;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getBankInfo() {
        return bankInfo;
    }

    public void setBankInfo(String bankInfo) {
        this.bankInfo = bankInfo;
    }

    public String getInvoiceHeader() {
        return invoiceHeader;
    }

    public void setInvoiceHeader(String invoiceHeader) {
        this.invoiceHeader = invoiceHeader;
    }

}