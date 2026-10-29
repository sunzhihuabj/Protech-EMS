package com.projlife.org.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 字典项入参 */
public class DictItemReq {

    @NotNull(message = "类型 id 不能为空")
    public Long typeId;

    @NotBlank(message = "项编码不能为空")
    public String itemCode;

    @NotBlank(message = "项名称不能为空")
    public String itemLabel;

    public String itemValue;
    public Integer sortNo;
    public Integer isDefault;
    public String status;
}
