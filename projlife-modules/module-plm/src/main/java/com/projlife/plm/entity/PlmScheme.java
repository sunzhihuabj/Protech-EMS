package com.projlife.plm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PLM plm_scheme（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("plm_scheme")
public class PlmScheme extends BaseEntity {

    /** 项目 id */
    private Long projectId;

    /** 方案编号 */
    private String schemeNo;

    /** 方案名称 */
    private String schemeName;

    /** 方案类型 */
    private String schemeType;

    /** 版本 */
    private String version;

    /** 会审状态 */
    private String reviewStatus;

    /** 会审意见 */
    private String reviewOpinion;

    /** 会审记录 */
    private Long reviewMeetingId;

    /** 附件 */
    private String attachIds;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getSchemeNo() {
        return schemeNo;
    }

    public void setSchemeNo(String schemeNo) {
        this.schemeNo = schemeNo;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
    }

    public String getSchemeType() {
        return schemeType;
    }

    public void setSchemeType(String schemeType) {
        this.schemeType = schemeType;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewOpinion() {
        return reviewOpinion;
    }

    public void setReviewOpinion(String reviewOpinion) {
        this.reviewOpinion = reviewOpinion;
    }

    public Long getReviewMeetingId() {
        return reviewMeetingId;
    }

    public void setReviewMeetingId(Long reviewMeetingId) {
        this.reviewMeetingId = reviewMeetingId;
    }

    public String getAttachIds() {
        return attachIds;
    }

    public void setAttachIds(String attachIds) {
        this.attachIds = attachIds;
    }

}