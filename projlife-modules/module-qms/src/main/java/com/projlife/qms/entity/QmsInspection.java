package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-QMS qms_inspection（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("qms_inspection")
public class QmsInspection extends VersionedEntity {

    /** QC-{IQC/IPQC/OQC}-{YYYYMMDD}-{3位} */
    private String inspectionNo;

    /** E-27 */
    private String inspectionType;

    /** 采购到货/工序/生产订单/施工 */
    private String sourceType;

    /** 来源 id */
    private Long sourceId;

    /** 物料 */
    private Long materialId;

    /** 批次 */
    private String batchNo;

    /** 抽样数 */
    private Integer sampleSize;

    /** 不良数 */
    private Integer defectCount;

    /** AQL 等级 */
    private String aqlLevel;

    /** E-28 PASS/CONCESSION/REJECT/SCREEN */
    private String result;

    /** 检验员 */
    private Long inspectorId;

    /** 检验时间 */
    private LocalDateTime inspectionTime;

    /** AI 检测原始返回 */
    private String aiResultJson;

    /** AI 置信度 */
    private BigDecimal aiConfidence;

    /** 人工是否改判 */
    private Integer humanCorrected;

    /** 照片 */
    private String photoFileIds;

    /** 备注 */
    private String remark;

    public String getInspectionNo() {
        return inspectionNo;
    }

    public void setInspectionNo(String inspectionNo) {
        this.inspectionNo = inspectionNo;
    }

    public String getInspectionType() {
        return inspectionType;
    }

    public void setInspectionType(String inspectionType) {
        this.inspectionType = inspectionType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(Integer sampleSize) {
        this.sampleSize = sampleSize;
    }

    public Integer getDefectCount() {
        return defectCount;
    }

    public void setDefectCount(Integer defectCount) {
        this.defectCount = defectCount;
    }

    public String getAqlLevel() {
        return aqlLevel;
    }

    public void setAqlLevel(String aqlLevel) {
        this.aqlLevel = aqlLevel;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Long getInspectorId() {
        return inspectorId;
    }

    public void setInspectorId(Long inspectorId) {
        this.inspectorId = inspectorId;
    }

    public LocalDateTime getInspectionTime() {
        return inspectionTime;
    }

    public void setInspectionTime(LocalDateTime inspectionTime) {
        this.inspectionTime = inspectionTime;
    }

    public String getAiResultJson() {
        return aiResultJson;
    }

    public void setAiResultJson(String aiResultJson) {
        this.aiResultJson = aiResultJson;
    }

    public BigDecimal getAiConfidence() {
        return aiConfidence;
    }

    public void setAiConfidence(BigDecimal aiConfidence) {
        this.aiConfidence = aiConfidence;
    }

    public Integer getHumanCorrected() {
        return humanCorrected;
    }

    public void setHumanCorrected(Integer humanCorrected) {
        this.humanCorrected = humanCorrected;
    }

    public String getPhotoFileIds() {
        return photoFileIds;
    }

    public void setPhotoFileIds(String photoFileIds) {
        this.photoFileIds = photoFileIds;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}