package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES mes_process_order（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("mes_process_order")
public class MesProcessOrder extends VersionedEntity {

    /** WS-{MO后6位}-{2位} */
    private String processNo;

    /** 生产订单 */
    private Long productionOrderId;

    /** 工序号 */
    private Integer processSeq;

    /** 工序编码 */
    private String processCode;

    /** 工序名 */
    private String processName;

    /** E-25 SERIAL/PARALLEL/CONDITION */
    private String processType;

    /** 工作中心 */
    private Long workCenterId;

    /** 标准工时 */
    private BigDecimal standardHours;

    /** 计划数量 >0 */
    private BigDecimal plannedQty;

    /** 合格数 */
    private BigDecimal goodQty;

    /** 不良数 */
    private BigDecimal defectQty;

    /** 报废数 */
    private BigDecimal scrapQty;

    /** E-26 TODO/DOING/WAIT_QC/DONE/LOCKED */
    private String status;

    /** 需报检 */
    private Integer needInspection;

    /** 开工时间 */
    private LocalDateTime startTime;

    /** 完工时间 */
    private LocalDateTime endTime;

    /** 操作工 */
    private Long operatorId;

    /** 不良锁定原因 */
    private String lockedReason;

    public String getProcessNo() {
        return processNo;
    }

    public void setProcessNo(String processNo) {
        this.processNo = processNo;
    }

    public Long getProductionOrderId() {
        return productionOrderId;
    }

    public void setProductionOrderId(Long productionOrderId) {
        this.productionOrderId = productionOrderId;
    }

    public Integer getProcessSeq() {
        return processSeq;
    }

    public void setProcessSeq(Integer processSeq) {
        this.processSeq = processSeq;
    }

    public String getProcessCode() {
        return processCode;
    }

    public void setProcessCode(String processCode) {
        this.processCode = processCode;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    public Long getWorkCenterId() {
        return workCenterId;
    }

    public void setWorkCenterId(Long workCenterId) {
        this.workCenterId = workCenterId;
    }

    public BigDecimal getStandardHours() {
        return standardHours;
    }

    public void setStandardHours(BigDecimal standardHours) {
        this.standardHours = standardHours;
    }

    public BigDecimal getPlannedQty() {
        return plannedQty;
    }

    public void setPlannedQty(BigDecimal plannedQty) {
        this.plannedQty = plannedQty;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getNeedInspection() {
        return needInspection;
    }

    public void setNeedInspection(Integer needInspection) {
        this.needInspection = needInspection;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getLockedReason() {
        return lockedReason;
    }

    public void setLockedReason(String lockedReason) {
        this.lockedReason = lockedReason;
    }

}