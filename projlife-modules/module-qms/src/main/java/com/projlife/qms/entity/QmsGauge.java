package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-QMS qms_gauge（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("qms_gauge")
public class QmsGauge extends BaseEntity {

    /** 量具编码 */
    private String gaugeCode;

    /** 名称 */
    private String gaugeName;

    /** 型号 */
    private String model;

    /** 精度（原 precision，MySQL 5.7 保留字改名 A-29） */
    private String precisionClass;

    /** 校准周期天 */
    private Integer calibrateCycleDays;

    /** 上次校准 */
    private LocalDate lastCalibrateDate;

    /** 下次校准（超期锁定） */
    private LocalDate nextCalibrateDate;

    /** 位置 */
    private String location;

    /** E-02 */
    private String status;

    public String getGaugeCode() {
        return gaugeCode;
    }

    public void setGaugeCode(String gaugeCode) {
        this.gaugeCode = gaugeCode;
    }

    public String getGaugeName() {
        return gaugeName;
    }

    public void setGaugeName(String gaugeName) {
        this.gaugeName = gaugeName;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrecisionClass() {
        return precisionClass;
    }

    public void setPrecisionClass(String precisionClass) {
        this.precisionClass = precisionClass;
    }

    public Integer getCalibrateCycleDays() {
        return calibrateCycleDays;
    }

    public void setCalibrateCycleDays(Integer calibrateCycleDays) {
        this.calibrateCycleDays = calibrateCycleDays;
    }

    public LocalDate getLastCalibrateDate() {
        return lastCalibrateDate;
    }

    public void setLastCalibrateDate(LocalDate lastCalibrateDate) {
        this.lastCalibrateDate = lastCalibrateDate;
    }

    public LocalDate getNextCalibrateDate() {
        return nextCalibrateDate;
    }

    public void setNextCalibrateDate(LocalDate nextCalibrateDate) {
        this.nextCalibrateDate = nextCalibrateDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}