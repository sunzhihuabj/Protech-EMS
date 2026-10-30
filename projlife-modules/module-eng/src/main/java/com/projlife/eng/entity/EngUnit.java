package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_unit（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_unit")
public class EngUnit extends VersionedEntity {

    /** U-{project_code}-{3位} UK */
    private String unitNo;

    /** 项目档案 */
    private Long mdProjectId;

    /** 机位名（A01） */
    private String unitName;

    /** 机型 */
    private String turbineModel;

    /** 机组序列号 */
    private String turbineSn;

    /** 产品线 */
    private String productLines;

    /** 安装日 */
    private LocalDate installDate;

    /** 调试日 */
    private LocalDate debugDate;

    /** 验收日 */
    private LocalDate acceptDate;

    /** 质保起 */
    private LocalDate warrantyStart;

    /** 质保止 */
    private LocalDate warrantyEnd;

    /** E-29 机组状态机 */
    private String status;

    /** SBOM（设备档案合一） */
    private Long sbomHeaderId;

    /** 安装位置 */
    private String locationDesc;

    /** 设备二维码 */
    private String qrCode;

    public String getUnitNo() {
        return unitNo;
    }

    public void setUnitNo(String unitNo) {
        this.unitNo = unitNo;
    }

    public Long getMdProjectId() {
        return mdProjectId;
    }

    public void setMdProjectId(Long mdProjectId) {
        this.mdProjectId = mdProjectId;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public String getTurbineModel() {
        return turbineModel;
    }

    public void setTurbineModel(String turbineModel) {
        this.turbineModel = turbineModel;
    }

    public String getTurbineSn() {
        return turbineSn;
    }

    public void setTurbineSn(String turbineSn) {
        this.turbineSn = turbineSn;
    }

    public String getProductLines() {
        return productLines;
    }

    public void setProductLines(String productLines) {
        this.productLines = productLines;
    }

    public LocalDate getInstallDate() {
        return installDate;
    }

    public void setInstallDate(LocalDate installDate) {
        this.installDate = installDate;
    }

    public LocalDate getDebugDate() {
        return debugDate;
    }

    public void setDebugDate(LocalDate debugDate) {
        this.debugDate = debugDate;
    }

    public LocalDate getAcceptDate() {
        return acceptDate;
    }

    public void setAcceptDate(LocalDate acceptDate) {
        this.acceptDate = acceptDate;
    }

    public LocalDate getWarrantyStart() {
        return warrantyStart;
    }

    public void setWarrantyStart(LocalDate warrantyStart) {
        this.warrantyStart = warrantyStart;
    }

    public LocalDate getWarrantyEnd() {
        return warrantyEnd;
    }

    public void setWarrantyEnd(LocalDate warrantyEnd) {
        this.warrantyEnd = warrantyEnd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getSbomHeaderId() {
        return sbomHeaderId;
    }

    public void setSbomHeaderId(Long sbomHeaderId) {
        this.sbomHeaderId = sbomHeaderId;
    }

    public String getLocationDesc() {
        return locationDesc;
    }

    public void setLocationDesc(String locationDesc) {
        this.locationDesc = locationDesc;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

}