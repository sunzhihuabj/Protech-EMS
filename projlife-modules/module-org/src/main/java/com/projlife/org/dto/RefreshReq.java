package com.projlife.org.dto;

import javax.validation.constraints.NotBlank;

/** 刷新令牌入参 */
public class RefreshReq {

    @NotBlank(message = "refreshToken 不能为空")
    public String refreshToken;
}
