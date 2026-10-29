package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_location（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_location")
public class MdLocation extends BaseEntity {

    /** {仓库}-{库区}-{排}-{列}-{层} */
    private String locationCode;

    /** 仓库码 YL/CP/QT/GC */
    private String warehouseCode;

    /** 库区码 */
    private String zoneCode;

    /** 排 */
    private String rowNo;

    /** 列 */
    private String colNo;

    /** 层 */
    private String layerNo;

    /** 货位类型 */
    private String locationType;

    /** 容量 */
    private BigDecimal capacity;

    /** E-05 */
    private String abcClass;

    /** 三色标识 RED/YELLOW/BLUE */
    private String colorFlag;

    /** 二维码内容 */
    private String qrCode;

    /** E-02 */
    private String status;

    public String getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public void setZoneCode(String zoneCode) {
        this.zoneCode = zoneCode;
    }

    public String getRowNo() {
        return rowNo;
    }

    public void setRowNo(String rowNo) {
        this.rowNo = rowNo;
    }

    public String getColNo() {
        return colNo;
    }

    public void setColNo(String colNo) {
        this.colNo = colNo;
    }

    public String getLayerNo() {
        return layerNo;
    }

    public void setLayerNo(String layerNo) {
        this.layerNo = layerNo;
    }

    public String getLocationType() {
        return locationType;
    }

    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }

    public BigDecimal getCapacity() {
        return capacity;
    }

    public void setCapacity(BigDecimal capacity) {
        this.capacity = capacity;
    }

    public String getAbcClass() {
        return abcClass;
    }

    public void setAbcClass(String abcClass) {
        this.abcClass = abcClass;
    }

    public String getColorFlag() {
        return colorFlag;
    }

    public void setColorFlag(String colorFlag) {
        this.colorFlag = colorFlag;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}