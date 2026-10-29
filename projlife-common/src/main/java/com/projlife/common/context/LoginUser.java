package com.projlife.common.context;

import java.util.Collections;
import java.util.Set;

/**
 * 登录用户上下文载体（纯数据，无行为）。
 * 由安全层过滤器填充；公共层（审计字段填充）与安全层（权限/数据范围）共同消费。
 */
public class LoginUser {

    private Long userId;
    private String userCode;
    private String username;
    private String realName;
    private Long deptId;
    /** 用户类型：INTERNAL / SUPPLIER / WORKER（E-01） */
    private String userType;
    /** 数据范围档位（E-35）：SELF/DEPT/DEPT_SUB/PROJECT/ALL */
    private String dataScope;
    /** 最高档数据范围取角色并集最大者 */
    private Set<String> roleCodes = Collections.emptySet();
    /** 权限码集合（含通配授权解析后的结果） */
    private Set<String> permissions = Collections.emptySet();
    /** 水印用真实姓名与手机号 */
    private String phone;

    public boolean hasPermission(String permCode) {
        return permissions != null && permissions.contains(permCode);
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
    }

    public Set<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(Set<String> roleCodes) {
        this.roleCodes = roleCodes == null ? Collections.emptySet() : roleCodes;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions == null ? Collections.emptySet() : permissions;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
