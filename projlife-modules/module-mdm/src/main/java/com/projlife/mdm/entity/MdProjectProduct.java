package com.projlife.mdm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MDM md_project_product（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("md_project_product")
public class MdProjectProduct extends BaseEntity {

    /** 项目档案 id */
    private Long mdProjectId;

    /** E-08 产品线 */
    private String productLine;

    /** 关联合同 */
    private Long contractId;

    /** 台数/套数 */
    private BigDecimal setQty;

    /** 质保起 */
    private LocalDate warrantyStart;

    /** 质保止 */
    private LocalDate warrantyEnd;

    /** 备注 */
    private String remark;

    public Long getMdProjectId() {
        return mdProjectId;
    }

    public void setMdProjectId(Long mdProjectId) {
        this.mdProjectId = mdProjectId;
    }

    public String getProductLine() {
        return productLine;
    }

    public void setProductLine(String productLine) {
        this.productLine = productLine;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public BigDecimal getSetQty() {
        return setQty;
    }

    public void setSetQty(BigDecimal setQty) {
        this.setQty = setQty;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}