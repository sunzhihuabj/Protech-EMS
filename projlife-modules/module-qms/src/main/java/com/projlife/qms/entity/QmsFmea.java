package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-QMS qms_fmea（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("qms_fmea")
public class QmsFmea extends BaseEntity {

    /** 产品/BOM */
    private Long productId;

    /** 工序 */
    private String processName;

    /** 失效模式 */
    private String failureMode;

    /** 失效影响 */
    private String failureEffect;

    /** 严重度 S */
    private Integer severity;

    /** 失效原因 */
    private String failureCause;

    /** 频度 O */
    private Integer occurrence;

    /** 现行控制 */
    private String currentControl;

    /** 探测度 D */
    private Integer detection;

    /** RPN=S×O×D */
    private Integer rpn;

    /** 建议措施 */
    private String recommendedAction;

    /** 向量预留（相似检索） */
    private String embedding;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getFailureMode() {
        return failureMode;
    }

    public void setFailureMode(String failureMode) {
        this.failureMode = failureMode;
    }

    public String getFailureEffect() {
        return failureEffect;
    }

    public void setFailureEffect(String failureEffect) {
        this.failureEffect = failureEffect;
    }

    public Integer getSeverity() {
        return severity;
    }

    public void setSeverity(Integer severity) {
        this.severity = severity;
    }

    public String getFailureCause() {
        return failureCause;
    }

    public void setFailureCause(String failureCause) {
        this.failureCause = failureCause;
    }

    public Integer getOccurrence() {
        return occurrence;
    }

    public void setOccurrence(Integer occurrence) {
        this.occurrence = occurrence;
    }

    public String getCurrentControl() {
        return currentControl;
    }

    public void setCurrentControl(String currentControl) {
        this.currentControl = currentControl;
    }

    public Integer getDetection() {
        return detection;
    }

    public void setDetection(Integer detection) {
        this.detection = detection;
    }

    public Integer getRpn() {
        return rpn;
    }

    public void setRpn(Integer rpn) {
        this.rpn = rpn;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

}