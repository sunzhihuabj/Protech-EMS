package com.projlife.org.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 修改密码入参（首登强改/自助改密） */
public class ChangePasswordReq {

    @NotBlank(message = "原密码不能为空")
    public String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "新密码长度需在 8-64 位之间")
    public String newPassword;
}
