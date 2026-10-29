package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM pcs_process_doc（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("pcs_process_doc")
public class PcsProcessDoc extends BaseEntity {

    /** TLD-GY{3位}-{3位} */
    private String docNo;

    /** E-08 产品线 */
    private String productLine;

    /** 文件类型（作业指导书/工艺流程/PFMEA 等） */
    private String docType;

    /** 文件名称 */
    private String docName;

    /** 版本 */
    private String version;

    /** 临时文件（样机阶段） */
    private Integer isTemp;

    /** DRAFT/PUBLISHED/PAUSED/ARCHIVED */
    private String status;

    /** 适用 BOM 版本 */
    private Long bomHeaderId;

    /** 文件 id */
    private Long fileId;

    /** 批准人 */
    private Long approvedBy;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    public String getDocNo() {
        return docNo;
    }

    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    public String getProductLine() {
        return productLine;
    }

    public void setProductLine(String productLine) {
        this.productLine = productLine;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getIsTemp() {
        return isTemp;
    }

    public void setIsTemp(Integer isTemp) {
        this.isTemp = isTemp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getBomHeaderId() {
        return bomHeaderId;
    }

    public void setBomHeaderId(Long bomHeaderId) {
        this.bomHeaderId = bomHeaderId;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

}