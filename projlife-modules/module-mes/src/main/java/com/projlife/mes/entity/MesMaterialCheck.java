package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES mes_material_check（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("mes_material_check")
public class MesMaterialCheck extends BaseEntity {

    /** 工序工单 */
    private Long processOrderId;

    /** 期望物料 */
    private Long materialId;

    /** 扫码原文 */
    private String scannedCode;

    /** 期望物料 id */
    private Long expectedMaterialId;

    /** 是否匹配 */
    private Integer isMatch;

    /** 比对时间 */
    private LocalDateTime checkTime;

    /** 操作工 */
    private Long operatorId;

    public Long getProcessOrderId() {
        return processOrderId;
    }

    public void setProcessOrderId(Long processOrderId) {
        this.processOrderId = processOrderId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public String getScannedCode() {
        return scannedCode;
    }

    public void setScannedCode(String scannedCode) {
        this.scannedCode = scannedCode;
    }

    public Long getExpectedMaterialId() {
        return expectedMaterialId;
    }

    public void setExpectedMaterialId(Long expectedMaterialId) {
        this.expectedMaterialId = expectedMaterialId;
    }

    public Integer getIsMatch() {
        return isMatch;
    }

    public void setIsMatch(Integer isMatch) {
        this.isMatch = isMatch;
    }

    public LocalDateTime getCheckTime() {
        return checkTime;
    }

    public void setCheckTime(LocalDateTime checkTime) {
        this.checkTime = checkTime;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

}