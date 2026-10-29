package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_project（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_project")
public class MdProject extends VersionedEntity {

    /** PJ{YYYY}-{NNNN} 永不复用 */
    private String projectCode;

    /** 标准名 = 业主规范名－地名（去尾缀）－期次 */
    private String stdName;

    /** 常用简称（现场口语） */
    private String shortName;

    /** 项目业务名 */
    private String projectName;

    /** 签约主体（合同甲方） */
    private Long customerId;

    /** 业主（初装可空 owner_pending=1） */
    private Long ownerId;

    /** 整机厂（纯技改可空） */
    private Long oemId;

    /** 我方签约主体 */
    private Long legalEntityId;

    /** E-07 订单类型 */
    private String orderType;

    /** 风场（排产单可空 farm_pending=1） */
    private Long farmId;

    /** 风场全称原文 */
    private String farmNameRaw;

    /** 期次 */
    private String phase;

    /** E-08 产品线数组 */
    private String productLines;

    /** 机型 */
    private String turbineModel;

    /** 机组数量 */
    private Integer turbineQty;

    /** 合同额（元） */
    private BigDecimal contractAmount;

    /** 台数/套数 >0 */
    private BigDecimal setQty;

    /** 质保起 */
    private LocalDate warrantyStart;

    /** 质保止（>start） */
    private LocalDate warrantyEnd;

    /** 质保月数 */
    private Integer warrantyMonths;

    /** 工程责任人（sys_user） */
    private Long engOwnerId;

    /** 商务责任人（sys_user） */
    private Long bizOwnerId;

    /** 售后责任人（质保期内必填） */
    private Long svcOwnerId;

    /** E-09 项目状态 */
    private String status;

    /** E-10 健康度 */
    private String health;

    /** 业主待补全标记 */
    private Integer ownerPending;

    /** 风场待补全标记 */
    private Integer farmPending;

    /** 备注 */
    private String remark;

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getStdName() {
        return stdName;
    }

    public void setStdName(String stdName) {
        this.stdName = stdName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getOemId() {
        return oemId;
    }

    public void setOemId(Long oemId) {
        this.oemId = oemId;
    }

    public Long getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(Long legalEntityId) {
        this.legalEntityId = legalEntityId;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public Long getFarmId() {
        return farmId;
    }

    public void setFarmId(Long farmId) {
        this.farmId = farmId;
    }

    public String getFarmNameRaw() {
        return farmNameRaw;
    }

    public void setFarmNameRaw(String farmNameRaw) {
        this.farmNameRaw = farmNameRaw;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getProductLines() {
        return productLines;
    }

    public void setProductLines(String productLines) {
        this.productLines = productLines;
    }

    public String getTurbineModel() {
        return turbineModel;
    }

    public void setTurbineModel(String turbineModel) {
        this.turbineModel = turbineModel;
    }

    public Integer getTurbineQty() {
        return turbineQty;
    }

    public void setTurbineQty(Integer turbineQty) {
        this.turbineQty = turbineQty;
    }

    public BigDecimal getContractAmount() {
        return contractAmount;
    }

    public void setContractAmount(BigDecimal contractAmount) {
        this.contractAmount = contractAmount;
    }

    public BigDecimal getSetQty() {
        return setQty;
    }

    public void setSetQty(BigDecimal setQty) {
        this.setQty = setQty;
    }

    public LocalDate getWarrantyStart() {
        return warrantyStart;
    }

    public void setWarrantyStart(LocalDate warrantyStart) {
        this.warrantyStart = warrantyStart;
    }

    public LocalDate getWarrantyEnd() {
        return warrantyEnd;
    }

    public void setWarrantyEnd(LocalDate warrantyEnd) {
        this.warrantyEnd = warrantyEnd;
    }

    public Integer getWarrantyMonths() {
        return warrantyMonths;
    }

    public void setWarrantyMonths(Integer warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    public Long getEngOwnerId() {
        return engOwnerId;
    }

    public void setEngOwnerId(Long engOwnerId) {
        this.engOwnerId = engOwnerId;
    }

    public Long getBizOwnerId() {
        return bizOwnerId;
    }

    public void setBizOwnerId(Long bizOwnerId) {
        this.bizOwnerId = bizOwnerId;
    }

    public Long getSvcOwnerId() {
        return svcOwnerId;
    }

    public void setSvcOwnerId(Long svcOwnerId) {
        this.svcOwnerId = svcOwnerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHealth() {
        return health;
    }

    public void setHealth(String health) {
        this.health = health;
    }

    public Integer getOwnerPending() {
        return ownerPending;
    }

    public void setOwnerPending(Integer ownerPending) {
        this.ownerPending = ownerPending;
    }

    public Integer getFarmPending() {
        return farmPending;
    }

    public void setFarmPending(Integer farmPending) {
        this.farmPending = farmPending;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}