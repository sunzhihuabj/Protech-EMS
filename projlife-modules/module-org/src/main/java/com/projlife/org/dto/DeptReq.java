package com.projlife.org.dto;

import javax.validation.constraints.NotBlank;

/** 部门维护入参 */
public class DeptReq {

    @NotBlank(message = "部门编码不能为空")
    public String deptCode;

    @NotBlank(message = "部门名称不能为空")
    public String deptName;

    public Long parentId;
    public String deptType = "DEPT";
    public Long leaderId;
    public Integer sortNo;
    public String status;
}
