package com.projlife.org.mapper;

/**
 * 用户分页查询条件（SysUserMapper.selectUserPage 入参）。
 */
public class UserPageQuery {

    private String keyword;

    private Long deptId;

    private String status;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
