package com.projlife.mes.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.AuditedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-MES mes_andon（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("mes_andon")
public class MesAndon extends AuditedEntity {

    /** AD-{YYYYMMDD}-{3位} */
    private String andonNo;

    /** 生产订单 */
    private Long productionOrderId;

    /** 工序工单 */
    private Long processOrderId;

    /** EQUIPMENT/MATERIAL/QUALITY/SAFETY */
    private String alertType;

    /** 升级级别 1-3 */
    private Integer alertLevel;

    /** 内容 */
    private String content;

    /** OPEN/RESPONDED/RESOLVED */
    private String status;

    /** 响应时间 */
    private LocalDateTime responseAt;

    /** 解决时间 */
    private LocalDateTime resolveAt;

    /** 升级时间 */
    private LocalDateTime escalateAt;

    /** 处理人 */
    private Long handlerId;

    public String getAndonNo() {
        return andonNo;
    }

    public void setAndonNo(String andonNo) {
        this.andonNo = andonNo;
    }

    public Long getProductionOrderId() {
        return productionOrderId;
    }

    public void setProductionOrderId(Long productionOrderId) {
        this.productionOrderId = productionOrderId;
    }

    public Long getProcessOrderId() {
        return processOrderId;
    }

    public void setProcessOrderId(Long processOrderId) {
        this.processOrderId = processOrderId;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public Integer getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(Integer alertLevel) {
        this.alertLevel = alertLevel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getResponseAt() {
        return responseAt;
    }

    public void setResponseAt(LocalDateTime responseAt) {
        this.responseAt = responseAt;
    }

    public LocalDateTime getResolveAt() {
        return resolveAt;
    }

    public void setResolveAt(LocalDateTime resolveAt) {
        this.resolveAt = resolveAt;
    }

    public LocalDateTime getEscalateAt() {
        return escalateAt;
    }

    public void setEscalateAt(LocalDateTime escalateAt) {
        this.escalateAt = escalateAt;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

}