package com.projlife.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-ARC arc_archive（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("arc_archive")
public class ArcArchive extends BaseEntity {

    /** PRT-{YYYY}-{门类}-{期限}-{4位} UK */
    private String archiveNo;

    /** E-41 门类 */
    private String category;

    /** 题名 */
    private String title;

    /** E-42 保管期限 */
    private String retention;

    /** 年度（H2 保留字改名 A-26） */
    private Integer archiveYear;

    /** 盒号 */
    private String boxNo;

    /** 存放位置 */
    private String location;

    /** 归档部门 */
    private Long deptId;

    /** 经办人 */
    private Long handlerId;

    /** 移交日期 */
    private LocalDate handoverDate;

    /** 关联合同 */
    private Long contractId;

    /** 关联项目 */
    private Long projectId;

    /** IN/OUT/DESTROYED */
    private String status;

    public String getArchiveNo() {
        return archiveNo;
    }

    public void setArchiveNo(String archiveNo) {
        this.archiveNo = archiveNo;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRetention() {
        return retention;
    }

    public void setRetention(String retention) {
        this.retention = retention;
    }

    public Integer getArchiveYear() {
        return archiveYear;
    }

    public void setArchiveYear(Integer archiveYear) {
        this.archiveYear = archiveYear;
    }

    public String getBoxNo() {
        return boxNo;
    }

    public void setBoxNo(String boxNo) {
        this.boxNo = boxNo;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getHandlerId() {
        return handlerId;
    }

    public void setHandlerId(Long handlerId) {
        this.handlerId = handlerId;
    }

    public LocalDate getHandoverDate() {
        return handoverDate;
    }

    public void setHandoverDate(LocalDate handoverDate) {
        this.handoverDate = handoverDate;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}