package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ENG eng_dispatch（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("eng_dispatch")
public class EngDispatch extends VersionedEntity {

    /** AS-{YYYYMMDD}-{4位} UK */
    private String dispatchNo;

    /** 项目档案（BR-03） */
    private Long mdProjectId;

    /** 风场（BR-04 必填） */
    private Long farmId;

    /** 合同 */
    private Long contractId;

    /** 发货触发来源 */
    private Long shipOrderId;

    /** 施工班组 */
    private Long crewId;

    /** 主责 */
    private Long leaderId;

    /** 成员 */
    private String memberIds;

    /** 计划开始 */
    private LocalDate planStart;

    /** 计划结束 */
    private LocalDate planEnd;

    /** 实际开始 */
    private LocalDateTime actualStart;

    /** 实际结束 */
    private LocalDateTime actualEnd;

    /** 涉及机组 */
    private String unitIds;

    /** 方案交底 */
    private Integer briefingDone;

    /** 安全交底 */
    private Integer safetyDone;

    /** E-32 派工状态机 */
    private String status;

    /** 进度 */
    private BigDecimal progressPct;

    public String getDispatchNo() {
        return dispatchNo;
    }

    public void setDispatchNo(String dispatchNo) {
        this.dispatchNo = dispatchNo;
    }

    public Long getMdProjectId() {
        return mdProjectId;
    }

    public void setMdProjectId(Long mdProjectId) {
        this.mdProjectId = mdProjectId;
    }

    public Long getFarmId() {
        return farmId;
    }

    public void setFarmId(Long farmId) {
        this.farmId = farmId;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public Long getShipOrderId() {
        return shipOrderId;
    }

    public void setShipOrderId(Long shipOrderId) {
        this.shipOrderId = shipOrderId;
    }

    public Long getCrewId() {
        return crewId;
    }

    public void setCrewId(Long crewId) {
        this.crewId = crewId;
    }

    public Long getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(Long leaderId) {
        this.leaderId = leaderId;
    }

    public String getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(String memberIds) {
        this.memberIds = memberIds;
    }

    public LocalDate getPlanStart() {
        return planStart;
    }

    public void setPlanStart(LocalDate planStart) {
        this.planStart = planStart;
    }

    public LocalDate getPlanEnd() {
        return planEnd;
    }

    public void setPlanEnd(LocalDate planEnd) {
        this.planEnd = planEnd;
    }

    public LocalDateTime getActualStart() {
        return actualStart;
    }

    public void setActualStart(LocalDateTime actualStart) {
        this.actualStart = actualStart;
    }

    public LocalDateTime getActualEnd() {
        return actualEnd;
    }

    public void setActualEnd(LocalDateTime actualEnd) {
        this.actualEnd = actualEnd;
    }

    public String getUnitIds() {
        return unitIds;
    }

    public void setUnitIds(String unitIds) {
        this.unitIds = unitIds;
    }

    public Integer getBriefingDone() {
        return briefingDone;
    }

    public void setBriefingDone(Integer briefingDone) {
        this.briefingDone = briefingDone;
    }

    public Integer getSafetyDone() {
        return safetyDone;
    }

    public void setSafetyDone(Integer safetyDone) {
        this.safetyDone = safetyDone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(BigDecimal progressPct) {
        this.progressPct = progressPct;
    }

}