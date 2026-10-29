package com.projlife.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.projlife.common.entity.BaseEntity;

/**
 * T-SYS 权限项：MENU（菜单）/ BUTTON（按钮）/ API（接口），树形。
 * 权限码格式 模块:操作[:对象]（03 册 §1.2）。
 */
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private String permCode;

    private String permName;

    private String module;

    /** MENU/BUTTON/API */
    private String permType;

    private Long parentId;

    private Integer sortNo;

    private String status;

    public String getPermCode() {
        return permCode;
    }

    public void setPermCode(String permCode) {
        this.permCode = permCode;
    }

    public String getPermName() {
        return permName;
    }

    public void setPermName(String permName) {
        this.permName = permName;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getPermType() {
        return permType;
    }

    public void setPermType(String permType) {
        this.permType = permType;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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
