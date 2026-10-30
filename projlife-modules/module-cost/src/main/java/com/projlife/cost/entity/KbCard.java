package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-KB kb_card（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("kb_card")
public class KbCard extends VersionedEntity {

    /** CARD-{YYYYMMDD}-{3位} UK */
    private String cardNo;

    /** 归属板 */
    private Long boardId;

    /** 事项（五要素 1） */
    private String title;

    /** 责任人（五要素 2） */
    private Long ownerId;

    /** 承诺日（五要素 3） */
    private LocalDate promiseDate;

    /** TODO/DOING/DONE/BLOCKED/ESCALATED（五要素 4） */
    private String status;

    /** 卡点说明（五要素 5，红灯必填） */
    private String blockReason;

    /** 补录标记（≤5%） */
    private Integer isBackfill;

    /** 创建人 */
    private String createdBy;

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public Long getBoardId() {
        return boardId;
    }

    public void setBoardId(Long boardId) {
        this.boardId = boardId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public LocalDate getPromiseDate() {
        return promiseDate;
    }

    public void setPromiseDate(LocalDate promiseDate) {
        this.promiseDate = promiseDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(String blockReason) {
        this.blockReason = blockReason;
    }

    public Integer getIsBackfill() {
        return isBackfill;
    }

    public void setIsBackfill(Integer isBackfill) {
        this.isBackfill = isBackfill;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

}