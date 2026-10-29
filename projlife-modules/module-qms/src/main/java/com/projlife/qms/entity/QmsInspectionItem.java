package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-QMS qms_inspection_item（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("qms_inspection_item")
public class QmsInspectionItem extends BaseEntity {

    /** 检验单 */
    private Long inspectionId;

    /** 检验项 */
    private String itemName;

    /** 标准值 */
    private String standardValue;

    /** 实测值 */
    private String actualValue;

    /** 合格 */
    private Integer isQualified;

    /** 缺陷描述 */
    private String defectDesc;

    public Long getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(Long inspectionId) {
        this.inspectionId = inspectionId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getStandardValue() {
        return standardValue;
    }

    public void setStandardValue(String standardValue) {
        this.standardValue = standardValue;
    }

    public String getActualValue() {
        return actualValue;
    }

    public void setActualValue(String actualValue) {
        this.actualValue = actualValue;
    }

    public Integer getIsQualified() {
        return isQualified;
    }

    public void setIsQualified(Integer isQualified) {
        this.isQualified = isQualified;
    }

    public String getDefectDesc() {
        return defectDesc;
    }

    public void setDefectDesc(String defectDesc) {
        this.defectDesc = defectDesc;
    }

}