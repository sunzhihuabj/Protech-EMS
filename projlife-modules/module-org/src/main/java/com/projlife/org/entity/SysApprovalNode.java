package com.projlife.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ORG sys_approval_node（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("sys_approval_node")
public class SysApprovalNode extends BaseEntity {

    /** 审批实例 id */
    private Long approvalId;

    /** 节点序 1 起 */
    private Integer nodeSeq;

    /** 节点名 */
    private String nodeName;

    /** 审批人 */
    private Long approverId;

    /** PENDING/APPROVED/REJECTED/PENDING_ADDSIGN */
    private String status;

    /** 意见 */
    private String comment;

    /** 操作时间 */
    private LocalDateTime actedAt;

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public Integer getNodeSeq() {
        return nodeSeq;
    }

    public void setNodeSeq(Integer nodeSeq) {
        this.nodeSeq = nodeSeq;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getActedAt() {
        return actedAt;
    }

    public void setActedAt(LocalDateTime actedAt) {
        this.actedAt = actedAt;
    }

}