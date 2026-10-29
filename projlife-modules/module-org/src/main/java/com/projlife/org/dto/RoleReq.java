package com.projlife.org.dto;

import javax.validation.constraints.NotBlank;
import java.util.List;

/** 角色新增/编辑入参（含权限码分配） */
public class RoleReq {

    @NotBlank(message = "角色编码不能为空")
    public String roleCode;

    @NotBlank(message = "角色名称不能为空")
    public String roleName;

    @NotBlank(message = "数据范围不能为空")
    public String dataScope;

    public String remark;
    public String status;
    public List<Long> permIds;
}
