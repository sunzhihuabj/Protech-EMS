package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES mes_process_param（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("mes_process_param")
public class MesProcessParam extends BaseEntity {

    /** 工序工单 */
    private Long processOrderId;

    /** 参数名 */
    private String paramName;

    /** 参数值 */
    private String paramValue;

    /** 标准下限 */
    private BigDecimal standardMin;

    /** 标准上限 */
    private BigDecimal standardMax;

    /** 是否合格 */
    private Integer isQualified;

    /** 记录时间 */
    private LocalDateTime recordTime;

    public Long getProcessOrderId() {
        return processOrderId;
    }

    public void setProcessOrderId(Long processOrderId) {
        this.processOrderId = processOrderId;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public String getParamValue() {
        return paramValue;
    }

    public void setParamValue(String paramValue) {
        this.paramValue = paramValue;
    }

    public BigDecimal getStandardMin() {
        return standardMin;
    }

    public void setStandardMin(BigDecimal standardMin) {
        this.standardMin = standardMin;
    }

    public BigDecimal getStandardMax() {
        return standardMax;
    }

    public void setStandardMax(BigDecimal standardMax) {
        this.standardMax = standardMax;
    }

    public Integer getIsQualified() {
        return isQualified;
    }

    public void setIsQualified(Integer isQualified) {
        this.isQualified = isQualified;
    }

    public LocalDateTime getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(LocalDateTime recordTime) {
        this.recordTime = recordTime;
    }

}