package com.projlife.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PRJ prj_template（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("prj_template")
public class PrjTemplate extends BaseEntity {

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 适用项目类型 */
    private String projectType;

    /** 默认模板 */
    private Integer isDefault;

    /** E-02 */
    private String status;

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}