package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-QMS qms_spc_data（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("qms_spc_data")
public class QmsSpcData extends BaseEntity {

    /** 特性 */
    private String characteristic;

    /** 采样时间 */
    private LocalDateTime sampleTime;

    /** 子组号 */
    private Integer subgroupNo;

    /** 单值 */
    private BigDecimal valueX;

    /** 子组均值 */
    private BigDecimal xBar;

    /** 极差 */
    private BigDecimal rValue;

    /** 上规格限 */
    private BigDecimal usl;

    /** 下规格限 */
    private BigDecimal lsl;

    /** 中心线 */
    private BigDecimal cl;

    /** 上控制限 */
    private BigDecimal ucl;

    /** 下控制限 */
    private BigDecimal lcl;

    /** 失控判异 */
    private Integer isOutOfControl;

    public String getCharacteristic() {
        return characteristic;
    }

    public void setCharacteristic(String characteristic) {
        this.characteristic = characteristic;
    }

    public LocalDateTime getSampleTime() {
        return sampleTime;
    }

    public void setSampleTime(LocalDateTime sampleTime) {
        this.sampleTime = sampleTime;
    }

    public Integer getSubgroupNo() {
        return subgroupNo;
    }

    public void setSubgroupNo(Integer subgroupNo) {
        this.subgroupNo = subgroupNo;
    }

    public BigDecimal getValueX() {
        return valueX;
    }

    public void setValueX(BigDecimal valueX) {
        this.valueX = valueX;
    }

    public BigDecimal getXBar() {
        return xBar;
    }

    public void setXBar(BigDecimal xBar) {
        this.xBar = xBar;
    }

    public BigDecimal getRValue() {
        return rValue;
    }

    public void setRValue(BigDecimal rValue) {
        this.rValue = rValue;
    }

    public BigDecimal getUsl() {
        return usl;
    }

    public void setUsl(BigDecimal usl) {
        this.usl = usl;
    }

    public BigDecimal getLsl() {
        return lsl;
    }

    public void setLsl(BigDecimal lsl) {
        this.lsl = lsl;
    }

    public BigDecimal getCl() {
        return cl;
    }

    public void setCl(BigDecimal cl) {
        this.cl = cl;
    }

    public BigDecimal getUcl() {
        return ucl;
    }

    public void setUcl(BigDecimal ucl) {
        this.ucl = ucl;
    }

    public BigDecimal getLcl() {
        return lcl;
    }

    public void setLcl(BigDecimal lcl) {
        this.lcl = lcl;
    }

    public Integer getIsOutOfControl() {
        return isOutOfControl;
    }

    public void setIsOutOfControl(Integer isOutOfControl) {
        this.isOutOfControl = isOutOfControl;
    }

}