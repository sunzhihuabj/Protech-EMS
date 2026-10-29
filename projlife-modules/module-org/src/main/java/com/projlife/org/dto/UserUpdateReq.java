package com.projlife.org.dto;

import java.util.List;

/** 用户编辑入参 */
public class UserUpdateReq {

    public String realName;
    public String phone;
    public Long deptId;
    public String position;
    public String userType;
    public Long legalEntityId;
    public String status;
    public List<Long> roleIds;
    public String remark;
}
