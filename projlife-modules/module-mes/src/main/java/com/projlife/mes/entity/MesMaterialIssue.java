package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES mes_material_issue（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("mes_material_issue")
public class MesMaterialIssue extends BaseEntity {

    /** 领料单号 */
    private String issueNo;

    /** 生产订单 */
    private Long productionOrderId;

    /** 物料 */
    private Long materialId;

    /** 需求数量 */
    private BigDecimal reqQty;

    /** 已发数量 */
    private BigDecimal issuedQty;

    /** 退库数量 */
    private BigDecimal returnQty;

    /** E-02 */
    private String status;

    /** 仓库 */
    private Long warehouseId;

    public String getIssueNo() {
        return issueNo;
    }

    public void setIssueNo(String issueNo) {
        this.issueNo = issueNo;
    }

    public Long getProductionOrderId() {
        return productionOrderId;
    }

    public void setProductionOrderId(Long productionOrderId) {
        this.productionOrderId = productionOrderId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public BigDecimal getReqQty() {
        return reqQty;
    }

    public void setReqQty(BigDecimal reqQty) {
        this.reqQty = reqQty;
    }

    public BigDecimal getIssuedQty() {
        return issuedQty;
    }

    public void setIssuedQty(BigDecimal issuedQty) {
        this.issuedQty = issuedQty;
    }

    public BigDecimal getReturnQty() {
        return returnQty;
    }

    public void setReturnQty(BigDecimal returnQty) {
        this.returnQty = returnQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

}