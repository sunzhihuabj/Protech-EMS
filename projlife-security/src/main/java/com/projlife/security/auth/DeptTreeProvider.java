package com.projlife.security.auth;

import java.util.List;

/**
 * 部门树查询接口（安全层定义，组织模块实现）。
 * DEPT_SUB 数据范围需要下级部门集合。
 */
public interface DeptTreeProvider {

    /** 返回部门及其全部下级部门 id（含自身） */
    List<Long> selfAndSubDeptIds(Long deptId);
}
