package com.projlife.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ORG sys_approval（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("sys_approval")
public class SysApproval extends VersionedEntity {

    /** AP-{YYYYMM}-{4位} UK */
    private String approvalNo;

    /** CONTRACT_REVIEW/ECO/ARC_BORROW/BUDGET */
    private String approvalType;

    /** 目标单据 id */
    private Long targetId;

    /** 目标描述 */
    private String targetDesc;

    /** 发起人 */
    private Long submitterId;

    /** PENDING/APPROVED/REJECTED/WITHDRAWN */
    private String status;

    /** 当前节点序 1 起 */
    private Integer currentNode;

    /** 总节点数 */
    private Integer totalNodes;

    public String getApprovalNo() {
        return approvalNo;
    }

    public void setApprovalNo(String approvalNo) {
        this.approvalNo = approvalNo;
    }

    public String getApprovalType() {
        return approvalType;
    }

    public void setApprovalType(String approvalType) {
        this.approvalType = approvalType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getTargetDesc() {
        return targetDesc;
    }

    public void setTargetDesc(String targetDesc) {
        this.targetDesc = targetDesc;
    }

    public Long getSubmitterId() {
        return submitterId;
    }

    public void setSubmitterId(Long submitterId) {
        this.submitterId = submitterId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(Integer currentNode) {
        this.currentNode = currentNode;
    }

    public Integer getTotalNodes() {
        return totalNodes;
    }

    public void setTotalNodes(Integer totalNodes) {
        this.totalNodes = totalNodes;
    }

}