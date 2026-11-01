package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_material（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_material")
public class MdMaterial extends BaseEntity {

    /** 8 位数字主干（字母前缀冻结新增） */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格 */
    private String spec;

    /** 物料详细描述 */
    private String description;

    /** E-20 计量单位（8 种受控） */
    private String unit;

    /** E-21 物料类别 */
    private String category;

    /** STD/SELF/PURCHASE/ELECTRIC/SHEET/VIRTUAL */
    private String materialType;

    /** 品牌 */
    private String brand;

    /** E-02 */
    private String status;

    /** 替代料编码 */
    private String substituteCode;

    /** 成本价（元） */
    private BigDecimal costPrice;

    /** 安全库存下限 */
    private BigDecimal safetyStockMin;

    /** 安全库存上限 */
    private BigDecimal safetyStockMax;

    /** E-05 ABC 分类 */
    private String abcClass;

    /** 受控物料（安全件/关键件） */
    private Integer isControlled;

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMaterialType() {
        return materialType;
    }

    public void setMaterialType(String materialType) {
        this.materialType = materialType;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubstituteCode() {
        return substituteCode;
    }

    public void setSubstituteCode(String substituteCode) {
        this.substituteCode = substituteCode;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getSafetyStockMin() {
        return safetyStockMin;
    }

    public void setSafetyStockMin(BigDecimal safetyStockMin) {
        this.safetyStockMin = safetyStockMin;
    }

    public BigDecimal getSafetyStockMax() {
        return safetyStockMax;
    }

    public void setSafetyStockMax(BigDecimal safetyStockMax) {
        this.safetyStockMax = safetyStockMax;
    }

    public String getAbcClass() {
        return abcClass;
    }

    public void setAbcClass(String abcClass) {
        this.abcClass = abcClass;
    }

    public Integer getIsControlled() {
        return isControlled;
    }

    public void setIsControlled(Integer isControlled) {
        this.isControlled = isControlled;
    }

}