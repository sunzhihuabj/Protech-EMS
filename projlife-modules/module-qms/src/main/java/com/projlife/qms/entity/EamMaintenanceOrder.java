package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-EAM eam_maintenance_order（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eam_maintenance_order")
public class EamMaintenanceOrder extends BaseEntity {

    /** 工单号 */
    private String orderNo;

    /** 设备 */
    private Long assetId;

    /** PREVENTIVE/REPAIR/INSPECTION */
    private String orderType;

    /** 内容 */
    private String content;

    /** 计划日期 */
    private LocalDate plannedDate;

    /** 实际日期 */
    private LocalDate actualDate;

    /** 工时 */
    private BigDecimal laborHours;

    /** 费用 */
    private BigDecimal cost;

    /** 备件 */
    private String sparePartsJson;

    /** PENDING/DONE/CLOSED */
    private String status;

    /** 技师 */
    private Long technicianId;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(LocalDate plannedDate) {
        this.plannedDate = plannedDate;
    }

    public LocalDate getActualDate() {
        return actualDate;
    }

    public void setActualDate(LocalDate actualDate) {
        this.actualDate = actualDate;
    }

    public BigDecimal getLaborHours() {
        return laborHours;
    }

    public void setLaborHours(BigDecimal laborHours) {
        this.laborHours = laborHours;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public String getSparePartsJson() {
        return sparePartsJson;
    }

    public void setSparePartsJson(String sparePartsJson) {
        this.sparePartsJson = sparePartsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public void setTechnicianId(Long technicianId) {
        this.technicianId = technicianId;
    }

}