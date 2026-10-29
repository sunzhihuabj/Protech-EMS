package com.projlife.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T-SYS-02 用户（02 册 §4.1 全字段，不得增删字段）。
 */
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 工号，唯一 */
    private String userCode;

    /** 登录名（手机号/工号），唯一 */
    private String username;

    /** 真实姓名（水印用） */
    private String realName;

    /** BCrypt（强度 10） */
    private String passwordHash;

    /** 水印用；脱敏展示 */
    private String phone;

    private Long deptId;

    /** 岗位 */
    private String position;

    /** E-01：INTERNAL/SUPPLIER/WORKER */
    private String userType;

    private Long legalEntityId;

    /** E-02：ACTIVE/FROZEN/PENDING */
    private String status;

    /** 首登强制改密，默认 1 */
    private Integer mustChangePwd;

    private LocalDateTime pwdUpdatedAt;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    /** 第三方账号有效期 */
    private LocalDate validFrom;

    private LocalDate validTo;

    /** 小程序绑定 */
    private String wxOpenid;

    /** 个人主题偏好（12 套预设，03 册 §6A）；空=跟随系统默认 */
    private String themeCode;

    private String remark;

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public Long getLegalEntityId() {
        return legalEntityId;
    }

    public void setLegalEntityId(Long legalEntityId) {
        this.legalEntityId = legalEntityId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMustChangePwd() {
        return mustChangePwd;
    }

    public void setMustChangePwd(Integer mustChangePwd) {
        this.mustChangePwd = mustChangePwd;
    }

    public LocalDateTime getPwdUpdatedAt() {
        return pwdUpdatedAt;
    }

    public void setPwdUpdatedAt(LocalDateTime pwdUpdatedAt) {
        this.pwdUpdatedAt = pwdUpdatedAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public String getWxOpenid() {
        return wxOpenid;
    }

    public void setWxOpenid(String wxOpenid) {
        this.wxOpenid = wxOpenid;
    }

    public String getThemeCode() {
        return themeCode;
    }

    public void setThemeCode(String themeCode) {
        this.themeCode = themeCode;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
