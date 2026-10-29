package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_supplier（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_supplier")
public class MdSupplier extends BaseEntity {

    /** S{4位} */
    private String supplierCode;

    /** 供应商名称 */
    private String supplierName;

    /** 统一社会信用代码 */
    private String creditCode;

    /** E-36 供应商分级 */
    private String level;

    /** 主营品类 */
    private String category;

    /** 联系人 */
    private String contact;

    /** 联系电话 */
    private String phone;

    /** 付款条件 */
    private String paymentTerms;

    /** 准入中/合格/暂停/黑名单 */
    private String status;

    /** 黑名单标记 */
    private Integer blacklistFlag;

    public String getSupplierCode() {
        return supplierCode;
    }

    public void setSupplierCode(String supplierCode) {
        this.supplierCode = supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getCreditCode() {
        return creditCode;
    }

    public void setCreditCode(String creditCode) {
        this.creditCode = creditCode;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getBlacklistFlag() {
        return blacklistFlag;
    }

    public void setBlacklistFlag(Integer blacklistFlag) {
        this.blacklistFlag = blacklistFlag;
    }

}