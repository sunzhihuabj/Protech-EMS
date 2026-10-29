package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM plm_design_doc（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("plm_design_doc")
public class PlmDesignDoc extends BaseEntity {

    /** 项目 id */
    private Long projectId;

    /** ELECTRIC/MECHANICAL */
    private String designType;

    /** 文档类型 */
    private String docType;

    /** 文档名称 */
    private String docName;

    /** 版本 */
    private String version;

    /** 文件 id */
    private Long fileId;

    /** 审核人 */
    private Long reviewerId;

    /** 审核状态 */
    private String reviewStatus;

    /** 锁定（通过后锁定，修改走 ECO） */
    private Integer isLocked;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getDesignType() {
        return designType;
    }

    public void setDesignType(String designType) {
        this.designType = designType;
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

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Integer getIsLocked() {
        return isLocked;
    }

    public void setIsLocked(Integer isLocked) {
        this.isLocked = isLocked;
    }

}