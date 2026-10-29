package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-EAM eam_asset（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eam_asset")
public class EamAsset extends BaseEntity {

    /** 设备编码 */
    private String assetCode;

    /** 名称 */
    private String assetName;

    /** 类型 */
    private String assetType;

    /** 型号 */
    private String model;

    /** 序列号 */
    private String serialNo;

    /** 制造商 */
    private String manufacturer;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 保修止 */
    private LocalDate warrantyExpire;

    /** 位置 */
    private String location;

    /** E-02 */
    private String status;

    /** 部门 */
    private Long deptId;

    /** 保管人 */
    private Long keeperId;

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public LocalDate getWarrantyExpire() {
        return warrantyExpire;
    }

    public void setWarrantyExpire(LocalDate warrantyExpire) {
        this.warrantyExpire = warrantyExpire;
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

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getKeeperId() {
        return keeperId;
    }

    public void setKeeperId(Long keeperId) {
        this.keeperId = keeperId;
    }

}