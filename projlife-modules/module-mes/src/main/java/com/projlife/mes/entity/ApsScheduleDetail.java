package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES aps_schedule_detail（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("aps_schedule_detail")
public class ApsScheduleDetail extends BaseEntity {

    /** 排产单 */
    private Long scheduleId;

    /** 生产订单 */
    private Long productionOrderId;

    /** 工序工单 */
    private Long processOrderId;

    /** 资源（工作中心） */
    private Long resourceId;

    /** 计划开始 */
    private LocalDateTime plannedStart;

    /** 计划结束 */
    private LocalDateTime plannedEnd;

    /** 换线分钟 */
    private Integer setupMinutes;

    /** 加工分钟 */
    private Integer runMinutes;

    /** E-02 */
    private String status;

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public Long getProductionOrderId() {
        return productionOrderId;
    }

    public void setProductionOrderId(Long productionOrderId) {
        this.productionOrderId = productionOrderId;
    }

    public Long getProcessOrderId() {
        return processOrderId;
    }

    public void setProcessOrderId(Long processOrderId) {
        this.processOrderId = processOrderId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public LocalDateTime getPlannedStart() {
        return plannedStart;
    }

    public void setPlannedStart(LocalDateTime plannedStart) {
        this.plannedStart = plannedStart;
    }

    public LocalDateTime getPlannedEnd() {
        return plannedEnd;
    }

    public void setPlannedEnd(LocalDateTime plannedEnd) {
        this.plannedEnd = plannedEnd;
    }

    public Integer getSetupMinutes() {
        return setupMinutes;
    }

    public void setSetupMinutes(Integer setupMinutes) {
        this.setupMinutes = setupMinutes;
    }

    public Integer getRunMinutes() {
        return runMinutes;
    }

    public void setRunMinutes(Integer runMinutes) {
        this.runMinutes = runMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}