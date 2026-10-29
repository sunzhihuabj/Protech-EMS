package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM pcs_route_step（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("pcs_route_step")
public class PcsRouteStep extends BaseEntity {

    /** 工艺路线 id */
    private Long routeId;

    /** 工序号 */
    private Integer stepSeq;

    /** 工序名 */
    private String stepName;

    /** 工作中心 */
    private Long workCenterId;

    /** 标准工时（小时） */
    private BigDecimal standardHours;

    /** 关键工序 */
    private Integer isKey;

    /** 需检验 */
    private Integer needInspection;

    /** 说明 */
    private String description;

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }

    public Integer getStepSeq() {
        return stepSeq;
    }

    public void setStepSeq(Integer stepSeq) {
        this.stepSeq = stepSeq;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
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

    public Integer getIsKey() {
        return isKey;
    }

    public void setIsKey(Integer isKey) {
        this.isKey = isKey;
    }

    public Integer getNeedInspection() {
        return needInspection;
    }

    public void setNeedInspection(Integer needInspection) {
        this.needInspection = needInspection;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}