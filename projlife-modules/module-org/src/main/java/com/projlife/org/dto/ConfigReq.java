package com.projlife.org.dto;

import javax.validation.constraints.NotBlank;

/** 系统参数维护入参 */
public class ConfigReq {

    @NotBlank(message = "参数键不能为空")
    public String configKey;

    public String configValue;
    public String remark;
}
