package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM plm_requirement_trace（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("plm_requirement_trace")
public class PlmRequirementTrace extends BaseEntity {

    /** 项目 id */
    private Long projectId;

    /** 需求描述 */
    private String requirementDesc;

    /** 来源 */
    private String source;

    /** 设计参数 */
    private String designParam;

    /** 验证方法 */
    private String verifyMethod;

    /** 验证结果 */
    private String verifyResult;

    /** E-02 */
    private String status;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getRequirementDesc() {
        return requirementDesc;
    }

    public void setRequirementDesc(String requirementDesc) {
        this.requirementDesc = requirementDesc;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDesignParam() {
        return designParam;
    }

    public void setDesignParam(String designParam) {
        this.designParam = designParam;
    }

    public String getVerifyMethod() {
        return verifyMethod;
    }

    public void setVerifyMethod(String verifyMethod) {
        this.verifyMethod = verifyMethod;
    }

    public String getVerifyResult() {
        return verifyResult;
    }

    public void setVerifyResult(String verifyResult) {
        this.verifyResult = verifyResult;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}