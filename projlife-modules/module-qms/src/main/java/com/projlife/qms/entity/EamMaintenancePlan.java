package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-EAM eam_maintenance_plan（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eam_maintenance_plan")
public class EamMaintenancePlan extends BaseEntity {

    /** 设备 */
    private Long assetId;

    /** DAILY/WEEKLY/MONTHLY/QUARTERLY/YEARLY */
    private String planType;

    /** 保养内容 */
    private String content;

    /** 下次执行 */
    private LocalDate nextDate;

    /** 周期天 */
    private Integer cycleDays;

    /** 负责人 */
    private Long responsibleId;

    /** E-02 */
    private String status;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDate getNextDate() {
        return nextDate;
    }

    public void setNextDate(LocalDate nextDate) {
        this.nextDate = nextDate;
    }

    public Integer getCycleDays() {
        return cycleDays;
    }

    public void setCycleDays(Integer cycleDays) {
        this.cycleDays = cycleDays;
    }

    public Long getResponsibleId() {
        return responsibleId;
    }

    public void setResponsibleId(Long responsibleId) {
        this.responsibleId = responsibleId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}