package com.projlife.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

/**
 * T-SYS 部门（14 部门树，01 册 §组织基线）。
 */
@TableName("sys_department")
public class SysDepartment extends BaseEntity {

    private String deptCode;

    private String deptName;

    private Long parentId;

    /** COMPANY/DEPT/GROUP */
    private String deptType;

    private Long leaderId;

    /** 是否业务链主（九板四链，00 册 C-10） */
    private Integer isChainMaster;

    private Long legalEntityId;

    private Integer sortNo;

    private String status;

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getDeptType() {
        return deptType;
    }

    public void setDeptType(String deptType) {
        this.deptType = deptType;
    }

    public Long getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(Long leaderId) {
        this.leaderId = leaderId;
    }

    public Integer getIsChainMaster() {
        return isChainMaster;
    }

    public void setIsChainMaster(Integer isChainMaster) {
        this.isChainMaster = isChainMaster;
    }

    public Long getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(Long legalEntityId) {
        this.legalEntityId = legalEntityId;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
