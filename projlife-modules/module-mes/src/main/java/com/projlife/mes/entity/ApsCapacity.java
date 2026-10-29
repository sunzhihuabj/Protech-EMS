package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES aps_capacity（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("aps_capacity")
public class ApsCapacity extends BaseEntity {

    /** 资源 id */
    private Long resourceId;

    /** EQUIPMENT/PERSON/MOLD */
    private String resourceType;

    /** 日产能 */
    private BigDecimal capacityPerDay;

    /** 班次日历 */
    private String calendarJson;

    /** E-02 */
    private String status;

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public BigDecimal getCapacityPerDay() {
        return capacityPerDay;
    }

    public void setCapacityPerDay(BigDecimal capacityPerDay) {
        this.capacityPerDay = capacityPerDay;
    }

    public String getCalendarJson() {
        return calendarJson;
    }

    public void setCalendarJson(String calendarJson) {
        this.calendarJson = calendarJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}