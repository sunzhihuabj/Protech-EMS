package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ARC arc_transfer（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("arc_transfer")
public class ArcTransfer extends BaseEntity {

    /** 移交单号 */
    private String transferNo;

    /** 移交部门 */
    private Long fromDept;

    /** 接收部门 */
    private Long toDept;

    /** 档案清单 */
    private String archiveIds;

    /** 移交日期 */
    private LocalDate transferDate;

    /** 移交人 */
    private Long transferor;

    /** 接收人 */
    private Long receiver;

    /** 监交人 */
    private Long supervisor;

    /** E-02 */
    private String status;

    public String getTransferNo() {
        return transferNo;
    }

    public void setTransferNo(String transferNo) {
        this.transferNo = transferNo;
    }

    public Long getFromDept() {
        return fromDept;
    }

    public void setFromDept(Long fromDept) {
        this.fromDept = fromDept;
    }

    public Long getToDept() {
        return toDept;
    }

    public void setToDept(Long toDept) {
        this.toDept = toDept;
    }

    public String getArchiveIds() {
        return archiveIds;
    }

    public void setArchiveIds(String archiveIds) {
        this.archiveIds = archiveIds;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public void setTransferDate(LocalDate transferDate) {
        this.transferDate = transferDate;
    }

    public Long getTransferor() {
        return transferor;
    }

    public void setTransferor(Long transferor) {
        this.transferor = transferor;
    }

    public Long getReceiver() {
        return receiver;
    }

    public void setReceiver(Long receiver) {
        this.receiver = receiver;
    }

    public Long getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(Long supervisor) {
        this.supervisor = supervisor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}