package com.projlife.org.dto;

import javax.validation.constraints.Pattern;

/** 个人主题偏好入参（03 册 §6A：12 套预设；空串=清除个人偏好跟随系统默认） */
public class ThemeReq {

    @Pattern(regexp = "^$|^t(1[0-2]|[1-9])$", message = "主题必须是 t1-t12 之一或空")
    public String themeCode;
}
