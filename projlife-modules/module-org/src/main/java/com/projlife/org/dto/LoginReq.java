package com.projlife.org.dto;

import javax.validation.constraints.NotBlank;

/** 登录入参。client：web/mp/pda（BR-08 端限制判定依据） */
public class LoginReq {

    @NotBlank(message = "登录名不能为空")
    public String username;

    @NotBlank(message = "密码不能为空")
    public String password;

    public String client = "web";
}
