package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-QMS qms_inspection_standard（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("qms_inspection_standard")
public class QmsInspectionStandard extends BaseEntity {

    /** 物料类别 */
    private String materialCategory;

    /** E-27 IQC/IPQC/OQC */
    private String inspectionType;

    /** AQL 等级 */
    private String aqlLevel;

    /** 抽样方案 */
    private String samplePlanJson;

    /** 检验项目 */
    private String itemsJson;

    /** 版本 */
    private String version;

    /** E-02 */
    private String status;

    public String getMaterialCategory() {
        return materialCategory;
    }

    public void setMaterialCategory(String materialCategory) {
        this.materialCategory = materialCategory;
    }

    public String getInspectionType() {
        return inspectionType;
    }

    public void setInspectionType(String inspectionType) {
        this.inspectionType = inspectionType;
    }

    public String getAqlLevel() {
        return aqlLevel;
    }

    public void setAqlLevel(String aqlLevel) {
        this.aqlLevel = aqlLevel;
    }

    public String getSamplePlanJson() {
        return samplePlanJson;
    }

    public void setSamplePlanJson(String samplePlanJson) {
        this.samplePlanJson = samplePlanJson;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public void setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}