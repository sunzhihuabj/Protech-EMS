package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-QMS qms_calibration（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("qms_calibration")
public class QmsCalibration extends BaseEntity {

    /** 量具 */
    private Long gaugeId;

    /** 校准日期 */
    private LocalDate calibrateDate;

    /** 结果 */
    private String result;

    /** 证书号 */
    private String certificateNo;

    /** 机构 */
    private String agency;

    /** 下次校准 */
    private LocalDate nextDate;

    /** 证书附件 */
    private Long fileId;

    public Long getGaugeId() {
        return gaugeId;
    }

    public void setGaugeId(Long gaugeId) {
        this.gaugeId = gaugeId;
    }

    public LocalDate getCalibrateDate() {
        return calibrateDate;
    }

    public void setCalibrateDate(LocalDate calibrateDate) {
        this.calibrateDate = calibrateDate;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getCertificateNo() {
        return certificateNo;
    }

    public void setCertificateNo(String certificateNo) {
        this.certificateNo = certificateNo;
    }

    public String getAgency() {
        return agency;
    }

    public void setAgency(String agency) {
        this.agency = agency;
    }

    public LocalDate getNextDate() {
        return nextDate;
    }

    public void setNextDate(LocalDate nextDate) {
        this.nextDate = nextDate;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

}