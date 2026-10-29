package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM pcs_worktime（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("pcs_worktime")
public class PcsWorktime extends BaseEntity {

    /** 产品型号 */
    private String productModel;

    /** 工序名 */
    private String stepName;

    /** 标准工时 */
    private BigDecimal standardHours;

    /** 费率 id */
    private Long laborRateId;

    /** 生效日期 */
    private LocalDate effectiveDate;

    public String getProductModel() {
        return productModel;
    }

    public void setProductModel(String productModel) {
        this.productModel = productModel;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public BigDecimal getStandardHours() {
        return standardHours;
    }

    public void setStandardHours(BigDecimal standardHours) {
        this.standardHours = standardHours;
    }

    public Long getLaborRateId() {
        return laborRateId;
    }

    public void setLaborRateId(Long laborRateId) {
        this.laborRateId = laborRateId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

}