package com.projlife.org.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/** 用户新增入参 */
public class UserCreateReq {

    @NotBlank(message = "工号不能为空")
    public String userCode;

    @NotBlank(message = "登录名不能为空")
    public String username;

    @NotBlank(message = "姓名不能为空")
    public String realName;

    @NotBlank(message = "初始密码不能为空")
    public String password;

    public String phone;

    @NotNull(message = "部门不能为空")
    public Long deptId;

    public String position;

    public String userType = "INTERNAL";

    public Long legalEntityId;

    public List<Long> roleIds;

    public String remark;
}
