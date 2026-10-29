package com.projlife.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-PRJ prj_member（02 册字段字典，代码生成器产出，禁止手改字段）。
 */
@TableName("prj_member")
public class PrjMember extends BaseEntity {

    /** 执行项目 id */
    private Long projectId;

    /** 成员 */
    private Long userId;

    /** 项目内角色 */
    private String roleCode;

    /** 加入日期 */
    private LocalDate joinDate;

    /** 退出日期 */
    private LocalDate leaveDate;

    /** 在项标记 */
    private Integer isActive;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public LocalDate getLeaveDate() {
        return leaveDate;
    }

    public void setLeaveDate(LocalDate leaveDate) {
        this.leaveDate = leaveDate;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
    }

}