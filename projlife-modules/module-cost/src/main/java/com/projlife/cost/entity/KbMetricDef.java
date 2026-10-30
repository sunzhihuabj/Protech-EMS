package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-KB kb_metric_def（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("kb_metric_def")
public class KbMetricDef extends BaseEntity {

    /** 指标编码 UK */
    private String metricCode;

    /** 归属板 */
    private Long boardId;

    /** 指标名 */
    private String metricName;

    /** 公式 */
    private String formula;

    /** 取数来源 */
    private String source;

    /** 目标 */
    private BigDecimal target;

    /** 阈值（Y/R 分界） */
    private String thresholdJson;

    /** 手工指标（每板≤1） */
    private Integer isManual;

    public String getMetricCode() {
        return metricCode;
    }

    public void setMetricCode(String metricCode) {
        this.metricCode = metricCode;
    }

    public Long getBoardId() {
        return boardId;
    }

    public void setBoardId(Long boardId) {
        this.boardId = boardId;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public BigDecimal getTarget() {
        return target;
    }

    public void setTarget(BigDecimal target) {
        this.target = target;
    }

    public String getThresholdJson() {
        return thresholdJson;
    }

    public void setThresholdJson(String thresholdJson) {
        this.thresholdJson = thresholdJson;
    }

    public Integer getIsManual() {
        return isManual;
    }

    public void setIsManual(Integer isManual) {
        this.isManual = isManual;
    }

}