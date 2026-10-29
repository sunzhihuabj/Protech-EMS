package com.projlife.org.dto;

import javax.validation.constraints.NotBlank;

/** 管理员重置密码入参 */
public class ResetPasswordReq {

    @NotBlank(message = "新密码不能为空")
    public String newPassword;
}
