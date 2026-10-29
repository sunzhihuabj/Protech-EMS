package com.projlife.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.org.entity.SysUser;
import com.projlife.security.datascope.DataScopeRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis-Plus Mapper：用户（部门维度数据范围示例，S0-5） */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 分页查询（按部门过滤）；标注数据范围规则：SELF 按工号 / DEPT 按部门列（03 册 §1.1） */
    @DataScopeRule(deptColumn = "dept_id", userColumn = "created_by")
    @Select("<script>SELECT * FROM sys_user WHERE deleted = 0"
            + "<if test='q != null and q.keyword != null and q.keyword != \"\"'>"
            + " AND (username LIKE CONCAT('%', #{q.keyword}, '%') OR real_name LIKE CONCAT('%', #{q.keyword}, '%')"
            + " OR user_code LIKE CONCAT('%', #{q.keyword}, '%'))"
            + "</if>"
            + "<if test='q != null and q.deptId != null'> AND dept_id = #{q.deptId}</if>"
            + "<if test='q != null and q.status != null and q.status != \"\"'> AND status = #{q.status}</if>"
            + " ORDER BY id DESC</script>")
    Page<SysUser> selectUserPage(Page<SysUser> page, @Param("q") UserPageQuery q);
}
