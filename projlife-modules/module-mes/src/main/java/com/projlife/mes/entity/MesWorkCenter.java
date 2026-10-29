package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES mes_work_center（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("mes_work_center")
public class MesWorkCenter extends BaseEntity {

    /** 工作中心编码 */
    private String wcCode;

    /** 名称 */
    private String wcName;

    /** 类型 */
    private String wcType;

    /** 日产能 */
    private BigDecimal capacityPerDay;

    /** 部门 */
    private Long deptId;

    /** E-02 */
    private String status;

    public String getWcCode() {
        return wcCode;
    }

    public void setWcCode(String wcCode) {
        this.wcCode = wcCode;
    }

    public String getWcName() {
        return wcName;
    }

    public void setWcName(String wcName) {
        this.wcName = wcName;
    }

    public String getWcType() {
        return wcType;
    }

    public void setWcType(String wcType) {
        this.wcType = wcType;
    }

    public BigDecimal getCapacityPerDay() {
        return capacityPerDay;
    }

    public void setCapacityPerDay(BigDecimal capacityPerDay) {
        this.capacityPerDay = capacityPerDay;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}