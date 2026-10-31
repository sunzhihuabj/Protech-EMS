package com.projlife.org.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 审批完成事件（R8：解耦审批引擎与业务模块——模块监听各自类型）。
 */
public class ApprovalCompletedEvent {

    private final String approvalType;
    private final Long targetId;
    private final boolean approved;

    public ApprovalCompletedEvent(String approvalType, Long targetId, boolean approved) {
        this.approvalType = approvalType;
        this.targetId = targetId;
        this.approved = approved;
    }

    public String getApprovalType() { return approvalType; }
    public Long getTargetId() { return targetId; }
    public boolean isApproved() { return approved; }
}
