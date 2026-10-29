package com.projlife.org.dto;

import javax.validation.constraints.Pattern;

/** 个人主题偏好入参（03 册 §6A：12 套预设，只允许从预设中选择） */
public class ThemeReq {

    @Pattern(regexp = "^t(1[0-2]|[1-9])$", message = "主题必须是 t1-t12 之一")
    public String themeCode;
}
