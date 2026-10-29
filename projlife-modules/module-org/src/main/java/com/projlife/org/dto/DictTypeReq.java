package com.projlife.org.dto;

import javax.validation.constraints.NotBlank;

/** 字典类型入参 */
public class DictTypeReq {

    @NotBlank(message = "类型编码不能为空")
    public String typeCode;

    @NotBlank(message = "类型名称不能为空")
    public String typeName;

    public String remark;
    public String status;
}
