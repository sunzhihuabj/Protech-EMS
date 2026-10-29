package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM plm_std_part（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("plm_std_part")
public class PlmStdPart extends BaseEntity {

    /** 标准件编码 */
    private String partCode;

    /** 名称 */
    private String partName;

    /** 规格 */
    private String spec;

    /** 类别 */
    private String category;

    /** 优选等级 */
    private String preferLevel;

    /** 使用次数 */
    private Integer usageCount;

    /** E-02 */
    private String status;

    public String getPartCode() {
        return partCode;
    }

    public void setPartCode(String partCode) {
        this.partCode = partCode;
    }

    public String getPartName() {
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPreferLevel() {
        return preferLevel;
    }

    public void setPreferLevel(String preferLevel) {
        this.preferLevel = preferLevel;
    }

    public Integer getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}