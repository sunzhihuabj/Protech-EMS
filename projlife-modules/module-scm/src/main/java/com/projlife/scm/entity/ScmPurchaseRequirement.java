package com.projlife.scm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-SCM scm_purchase_requirement（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("scm_purchase_requirement")
public class ScmPurchaseRequirement extends VersionedEntity {

    /** PR-{YYYYMM}-{4位} */
    private String reqNo;

    /** BOM_SHORTAGE/SAFETY_STOCK/MANUAL/STOCK */
    private String sourceType;

    /** 项目（①③必填） */
    private Long projectId;

    /** 物料 */
    private Long materialId;

    /** 数量 */
    private BigDecimal qty;

    /** 期望到货 */
    private LocalDate expectDate;

    /** OPEN/MERGED/CLOSED */
    private String status;

    /** 合并后 PO */
    private Long mergePoId;

    /** 请购人 */
    private Long requesterId;

    public String getReqNo() {
        return reqNo;
    }

    public void setReqNo(String reqNo) {
        this.reqNo = reqNo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public LocalDate getExpectDate() {
        return expectDate;
    }

    public void setExpectDate(LocalDate expectDate) {
        this.expectDate = expectDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getMergePoId() {
        return mergePoId;
    }

    public void setMergePoId(Long mergePoId) {
        this.mergePoId = mergePoId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

}