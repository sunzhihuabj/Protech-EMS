package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_customer（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_customer")
public class MdCustomer extends VersionedEntity {

    /** C{4位}，不可改 */
    private String customerCode;

    /** 规范全称（与营业执照一致） */
    private String customerName;

    /** 简称 */
    private String shortName;

    /** 上级（区域/项目公司→集团） */
    private Long parentId;

    /** E-03 GROUP/REGION/PROJECT */
    private String level;

    /** E-04 OWNER/OEM/RELATED/OTHER */
    private String customerType;

    /** 集团归并名（142→14） */
    private String groupName;

    /** 统一社会信用代码 */
    private String creditCode;

    /** 联系人（脱敏展示） */
    private String contactPerson;

    /** 联系电话（脱敏展示） */
    private String contactPhone;

    /** 地址 */
    private String address;

    /** E-05 客户分级 */
    private String levelAbc;

    /** 信用分 */
    private BigDecimal creditScore;

    /** 信用额度（元） */
    private BigDecimal creditLimit;

    /** 内部关联方（特固兰=1） */
    private Integer isInternal;

    /** E-02 */
    private String status;

    /** E-06 数据来源 */
    private String source;

    /** 备注 */
    private String remark;

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getCreditCode() {
        return creditCode;
    }

    public void setCreditCode(String creditCode) {
        this.creditCode = creditCode;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLevelAbc() {
        return levelAbc;
    }

    public void setLevelAbc(String levelAbc) {
        this.levelAbc = levelAbc;
    }

    public BigDecimal getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(BigDecimal creditScore) {
        this.creditScore = creditScore;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public Integer getIsInternal() {
        return isInternal;
    }

    public void setIsInternal(Integer isInternal) {
        this.isInternal = isInternal;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}