package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-COST cost_evm（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("cost_evm")
public class CostEvm extends BaseEntity {

    /** 项目 */
    private Long projectId;

    /** 快照日 */
    private LocalDate snapshotDate;

    /** 计划价值 */
    private BigDecimal pv;

    /** 挣值 */
    private BigDecimal ev;

    /** 实际成本 */
    private BigDecimal ac;

    /** SPI=EV/PV */
    private BigDecimal spi;

    /** CPI=EV/AC */
    private BigDecimal cpi;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public BigDecimal getPv() {
        return pv;
    }

    public void setPv(BigDecimal pv) {
        this.pv = pv;
    }

    public BigDecimal getEv() {
        return ev;
    }

    public void setEv(BigDecimal ev) {
        this.ev = ev;
    }

    public BigDecimal getAc() {
        return ac;
    }

    public void setAc(BigDecimal ac) {
        this.ac = ac;
    }

    public BigDecimal getSpi() {
        return spi;
    }

    public void setSpi(BigDecimal spi) {
        this.spi = spi;
    }

    public BigDecimal getCpi() {
        return cpi;
    }

    public void setCpi(BigDecimal cpi) {
        this.cpi = cpi;
    }

}