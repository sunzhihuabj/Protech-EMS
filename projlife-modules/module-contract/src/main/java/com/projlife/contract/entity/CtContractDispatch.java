package com.projlife.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-CT ct_contract_dispatch（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("ct_contract_dispatch")
public class CtContractDispatch extends BaseEntity {

    /** 合同 id */
    private Long contractId;

    /** 接收人（sys_user） */
    private Long receiverId;

    /** 交底摘要 */
    private String dispatchContent;

    /** 发送时间 */
    private LocalDateTime sentAt;

    /** 已阅时间 */
    private LocalDateTime readAt;

    /** 提醒次数 */
    private Integer remindCount;

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public String getDispatchContent() {
        return dispatchContent;
    }

    public void setDispatchContent(String dispatchContent) {
        this.dispatchContent = dispatchContent;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public Integer getRemindCount() {
        return remindCount;
    }

    public void setRemindCount(Integer remindCount) {
        this.remindCount = remindCount;
    }

}