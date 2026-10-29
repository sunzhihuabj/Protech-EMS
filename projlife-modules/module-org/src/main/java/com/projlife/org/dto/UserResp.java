package com.projlife.org.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 用户出参：不含密码哈希；手机号脱敏（02 册 §4.1：水印用；脱敏展示） */
public class UserResp {

    public Long id;
    public String userCode;
    public String username;
    public String realName;
    public String phone;
    public Long deptId;
    public String deptName;
    public String position;
    public String userType;
    public Long legalEntityId;
    public String status;
    public boolean mustChangePwd;
    public String themeCode;
    public LocalDateTime lastLoginAt;
    public String remark;
    public List<Long> roleIds;
    public List<String> roleNames;
}
