package com.projlife.eng.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-THD thd_worker_project（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("thd_worker_project")
public class ThdWorkerProject extends BaseEntity {

    /** 施工人员 */
    private Long workerId;

    /** 项目档案 */
    private Long mdProjectId;

    /** 授权起 */
    private LocalDate authStart;

    /** 授权止（过期立即失效） */
    private LocalDate authEnd;

    /** GRANTED/EXPIRED/REVOKED */
    private String status;

    /** 冻结原因 */
    private String frozenReason;

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

    public Long getMdProjectId() {
        return mdProjectId;
    }

    public void setMdProjectId(Long mdProjectId) {
        this.mdProjectId = mdProjectId;
    }

    public LocalDate getAuthStart() {
        return authStart;
    }

    public void setAuthStart(LocalDate authStart) {
        this.authStart = authStart;
    }

    public LocalDate getAuthEnd() {
        return authEnd;
    }

    public void setAuthEnd(LocalDate authEnd) {
        this.authEnd = authEnd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFrozenReason() {
        return frozenReason;
    }

    public void setFrozenReason(String frozenReason) {
        this.frozenReason = frozenReason;
    }

}