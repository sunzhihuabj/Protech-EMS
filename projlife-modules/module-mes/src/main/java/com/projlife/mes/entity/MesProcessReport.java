package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES mes_process_report（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("mes_process_report")
public class MesProcessReport extends BaseEntity {

    /** 工序工单 */
    private Long processOrderId;

    /** START/FINISH */
    private String reportType;

    /** 合格数 */
    private BigDecimal goodQty;

    /** 不良数 */
    private BigDecimal defectQty;

    /** 报废数 */
    private BigDecimal scrapQty;

    /** 工时 */
    private BigDecimal workHours;

    /** 报工人 */
    private Long operatorId;

    /** 报工时间 */
    private LocalDateTime reportTime;

    /** 补报工标记 */
    private Integer isBackfill;

    /** 备注 */
    private String remark;

    public Long getProcessOrderId() {
        return processOrderId;
    }

    public void setProcessOrderId(Long processOrderId) {
        this.processOrderId = processOrderId;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public BigDecimal getGoodQty() {
        return goodQty;
    }

    public void setGoodQty(BigDecimal goodQty) {
        this.goodQty = goodQty;
    }

    public BigDecimal getDefectQty() {
        return defectQty;
    }

    public void setDefectQty(BigDecimal defectQty) {
        this.defectQty = defectQty;
    }

    public BigDecimal getScrapQty() {
        return scrapQty;
    }

    public void setScrapQty(BigDecimal scrapQty) {
        this.scrapQty = scrapQty;
    }

    public BigDecimal getWorkHours() {
        return workHours;
    }

    public void setWorkHours(BigDecimal workHours) {
        this.workHours = workHours;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getReportTime() {
        return reportTime;
    }

    public void setReportTime(LocalDateTime reportTime) {
        this.reportTime = reportTime;
    }

    public Integer getIsBackfill() {
        return isBackfill;
    }

    public void setIsBackfill(Integer isBackfill) {
        this.isBackfill = isBackfill;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}