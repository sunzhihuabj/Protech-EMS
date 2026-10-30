package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM bom_header（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bom_header")
public class BomHeader extends BaseEntity {

    /** BOM-{形态}{视图}-{型号/项目}-V{n} */
    private String bomNo;

    /** E-17 A 标准/B 参数化/C 工程 */
    private String bomForm;

    /** E-18 EBOM/MBOM/SBOM */
    private String bomView;

    /** E-08 单值 */
    private String productLine;

    /** 型号 */
    private String productModel;

    /** 项目档案（C 类必填） */
    private Long mdProjectId;

    /** Golden BOM（A/B 类派生源） */
    private Long baseBomId;

    /** 视图转换来源 */
    private Long sourceBomId;

    /** 项目派生源 Golden BOM（FR-PLM-18） */
    private Long derivedFrom;

    /** V1.0 */
    private String version;

    /** 同对象唯一现行 */
    private Integer isCurrent;

    /** E-19 */
    private String status;

    /** C 类 MBOM 显式不适用 */
    private Integer mbomNotApplicable;

    /** B 类参数（塔高/节数/段长） */
    private String paramJson;

    /** C 类点位定义 */
    private String pointJson;

    /** 发布时间 */
    private LocalDateTime releasedAt;

    /** 13 条校验跑批结果快照 JSON 文本（A-34/A-30） */
    private String qcResult;

    /** 发布人 */
    private String releasedBy;

    /** 备注 */
    private String remark;

    public String getBomNo() {
        return bomNo;
    }

    public void setBomNo(String bomNo) {
        this.bomNo = bomNo;
    }

    public String getBomForm() {
        return bomForm;
    }

    public void setBomForm(String bomForm) {
        this.bomForm = bomForm;
    }

    public String getBomView() {
        return bomView;
    }

    public void setBomView(String bomView) {
        this.bomView = bomView;
    }

    public String getProductLine() {
        return productLine;
    }

    public void setProductLine(String productLine) {
        this.productLine = productLine;
    }

    public String getProductModel() {
        return productModel;
    }

    public void setProductModel(String productModel) {
        this.productModel = productModel;
    }

    public Long getMdProjectId() {
        return mdProjectId;
    }

    public void setMdProjectId(Long mdProjectId) {
        this.mdProjectId = mdProjectId;
    }

    public Long getBaseBomId() {
        return baseBomId;
    }

    public void setBaseBomId(Long baseBomId) {
        this.baseBomId = baseBomId;
    }

    public Long getSourceBomId() {
        return sourceBomId;
    }

    public void setSourceBomId(Long sourceBomId) {
        this.sourceBomId = sourceBomId;
    }

    public Long getDerivedFrom() {
        return derivedFrom;
    }

    public void setDerivedFrom(Long derivedFrom) {
        this.derivedFrom = derivedFrom;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getIsCurrent() {
        return isCurrent;
    }

    public void setIsCurrent(Integer isCurrent) {
        this.isCurrent = isCurrent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMbomNotApplicable() {
        return mbomNotApplicable;
    }

    public void setMbomNotApplicable(Integer mbomNotApplicable) {
        this.mbomNotApplicable = mbomNotApplicable;
    }

    public String getParamJson() {
        return paramJson;
    }

    public void setParamJson(String paramJson) {
        this.paramJson = paramJson;
    }

    public String getPointJson() {
        return pointJson;
    }

    public void setPointJson(String pointJson) {
        this.pointJson = pointJson;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getQcResult() {
        return qcResult;
    }

    public void setQcResult(String qcResult) {
        this.qcResult = qcResult;
    }

    public String getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(String releasedBy) {
        this.releasedBy = releasedBy;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}