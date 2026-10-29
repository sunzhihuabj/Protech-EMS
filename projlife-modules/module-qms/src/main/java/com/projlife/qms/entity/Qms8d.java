package com.projlife.qms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.VersionedEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-QMS qms_8d（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("qms_8d")
public class Qms8d extends VersionedEntity {

    /** 8D 编号 */
    private String reportNo;

    /** 问题描述 */
    private String problemDesc;

    /** D1 小组 */
    private String d1Team;

    /** D2 问题描述 */
    private String d2Problem;

    /** D3 临时围堵 */
    private String d3Containment;

    /** D4 根因 */
    private String d4RootCause;

    /** D5 纠正措施 */
    private String d5Corrective;

    /** D6 效果验证 */
    private String d6Verify;

    /** D7 预防措施 */
    private String d7Preventive;

    /** D8 团队认可 */
    private String d8Congratulate;

    /** OPEN/CLOSED */
    private String status;

    /** 负责人 */
    private Long ownerId;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    public String getReportNo() {
        return reportNo;
    }

    public void setReportNo(String reportNo) {
        this.reportNo = reportNo;
    }

    public String getProblemDesc() {
        return problemDesc;
    }

    public void setProblemDesc(String problemDesc) {
        this.problemDesc = problemDesc;
    }

    public String getD1Team() {
        return d1Team;
    }

    public void setD1Team(String d1Team) {
        this.d1Team = d1Team;
    }

    public String getD2Problem() {
        return d2Problem;
    }

    public void setD2Problem(String d2Problem) {
        this.d2Problem = d2Problem;
    }

    public String getD3Containment() {
        return d3Containment;
    }

    public void setD3Containment(String d3Containment) {
        this.d3Containment = d3Containment;
    }

    public String getD4RootCause() {
        return d4RootCause;
    }

    public void setD4RootCause(String d4RootCause) {
        this.d4RootCause = d4RootCause;
    }

    public String getD5Corrective() {
        return d5Corrective;
    }

    public void setD5Corrective(String d5Corrective) {
        this.d5Corrective = d5Corrective;
    }

    public String getD6Verify() {
        return d6Verify;
    }

    public void setD6Verify(String d6Verify) {
        this.d6Verify = d6Verify;
    }

    public String getD7Preventive() {
        return d7Preventive;
    }

    public void setD7Preventive(String d7Preventive) {
        this.d7Preventive = d7Preventive;
    }

    public String getD8Congratulate() {
        return d8Congratulate;
    }

    public void setD8Congratulate(String d8Congratulate) {
        this.d8Congratulate = d8Congratulate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

}