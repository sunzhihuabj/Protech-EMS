package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM bom_line（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("bom_line")
public class BomLine extends BaseEntity {

    /** BOM 头 */
    private Long bomHeaderId;

    /** 层级序号 ^\d+(-\d+){0,2}$ */
    private String lineNo;

    /** 物料（禁空禁序号） */
    private Long materialId;

    /** 物料编码快照 */
    private String materialCode;

    /** 单机用量 >0 */
    private BigDecimal qtyPerUnit;

    /** E-20 受控 8 种 */
    private String unit;

    /** 损耗率 0-0.5 */
    private BigDecimal lossRate;

    /** E-21 */
    private String materialCategory;

    /** 点位（C 类必填） */
    private String pointCode;

    /** 适用工艺文件 */
    private Long processDocId;

    /** 项目专用件 */
    private Integer isProjectSpecial;

    /** 替代料 */
    private Long substituteMaterialId;

    /** E-22 自制/外购/外协/标准件 */
    private String sourceType;

    /** 备注 */
    private String remark;

    public Long getBomHeaderId() {
        return bomHeaderId;
    }

    public void setBomHeaderId(Long bomHeaderId) {
        this.bomHeaderId = bomHeaderId;
    }

    public String getLineNo() {
        return lineNo;
    }

    public void setLineNo(String lineNo) {
        this.lineNo = lineNo;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public BigDecimal getQtyPerUnit() {
        return qtyPerUnit;
    }

    public void setQtyPerUnit(BigDecimal qtyPerUnit) {
        this.qtyPerUnit = qtyPerUnit;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getLossRate() {
        return lossRate;
    }

    public void setLossRate(BigDecimal lossRate) {
        this.lossRate = lossRate;
    }

    public String getMaterialCategory() {
        return materialCategory;
    }

    public void setMaterialCategory(String materialCategory) {
        this.materialCategory = materialCategory;
    }

    public String getPointCode() {
        return pointCode;
    }

    public void setPointCode(String pointCode) {
        this.pointCode = pointCode;
    }

    public Long getProcessDocId() {
        return processDocId;
    }

    public void setProcessDocId(Long processDocId) {
        this.processDocId = processDocId;
    }

    public Integer getIsProjectSpecial() {
        return isProjectSpecial;
    }

    public void setIsProjectSpecial(Integer isProjectSpecial) {
        this.isProjectSpecial = isProjectSpecial;
    }

    public Long getSubstituteMaterialId() {
        return substituteMaterialId;
    }

    public void setSubstituteMaterialId(Long substituteMaterialId) {
        this.substituteMaterialId = substituteMaterialId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}