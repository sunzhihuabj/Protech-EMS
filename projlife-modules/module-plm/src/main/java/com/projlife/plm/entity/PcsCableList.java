package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM pcs_cable_list（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("pcs_cable_list")
public class PcsCableList extends BaseEntity {

    /** 项目 id */
    private Long projectId;

    /** 线缆编号 */
    private String cableNo;

    /** 起点 */
    private String fromPoint;

    /** 终点 */
    private String toPoint;

    /** 型号 */
    private String model;

    /** 长度（米） */
    private BigDecimal length;

    /** 备注 */
    private String remark;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getCableNo() {
        return cableNo;
    }

    public void setCableNo(String cableNo) {
        this.cableNo = cableNo;
    }

    public String getFromPoint() {
        return fromPoint;
    }

    public void setFromPoint(String fromPoint) {
        this.fromPoint = fromPoint;
    }

    public String getToPoint() {
        return toPoint;
    }

    public void setToPoint(String toPoint) {
        this.toPoint = toPoint;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public BigDecimal getLength() {
        return length;
    }

    public void setLength(BigDecimal length) {
        this.length = length;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}