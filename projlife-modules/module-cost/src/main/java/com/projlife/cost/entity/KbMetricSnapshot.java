package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-KB kb_metric_snapshot（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("kb_metric_snapshot")
public class KbMetricSnapshot extends BaseEntity {

    /** 指标编码 */
    private String metricCode;

    /** 期间 */
    private String period;

    /** 值 */
    private BigDecimal metricValue;

    /** 目标 */
    private BigDecimal targetValue;

    /** GREEN/YELLOW/RED */
    private String status;

    public String getMetricCode() {
        return metricCode;
    }

    public void setMetricCode(String metricCode) {
        this.metricCode = metricCode;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public BigDecimal getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(BigDecimal metricValue) {
        this.metricValue = metricValue;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}