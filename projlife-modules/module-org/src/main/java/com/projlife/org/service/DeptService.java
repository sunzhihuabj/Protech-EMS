package com.projlife.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.org.dto.DeptReq;
import com.projlife.org.entity.SysDepartment;
import com.projlife.org.entity.SysUser;
import com.projlife.org.mapper.SysDepartmentMapper;
import com.projlife.org.mapper.SysUserMapper;
import com.projlife.org.service.audit.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门树维护（S0-15 种子为 14 部门）。删除校验：有子部门或有在职用户不可删。
 */
@Service
public class DeptService {

    private final SysDepartmentMapper deptMapper;
    private final SysUserMapper userMapper;

    public DeptService(SysDepartmentMapper deptMapper, SysUserMapper userMapper) {
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
    }

    public List<Map<String, Object>> tree() {
        List<SysDepartment> all = deptMapper.selectList(new LambdaQueryWrapper<SysDepartment>()
                .orderByAsc(SysDepartment::getSortNo));
        Map<Long, Map<String, Object>> nodeMap = new HashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (SysDepartment dept : all) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", dept.getId());
            node.put("deptCode", dept.getDeptCode());
            node.put("deptName", dept.getDeptName());
            node.put("parentId", dept.getParentId());
            node.put("deptType", dept.getDeptType());
            node.put("isChainMaster", dept.getIsChainMaster());
            node.put("status", dept.getStatus());
            node.put("sortNo", dept.getSortNo());
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(dept.getId(), node);
        }
        for (SysDepartment dept : all) {
            Map<String, Object> node = nodeMap.get(dept.getId());
            Map<String, Object> parent = dept.getParentId() == null ? null : nodeMap.get(dept.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(node);
            }
        }
        return roots;
    }

    @Audited(module = "org", action = "create_dept", targetType = "sys_department", targetId = "{0.deptCode}")
    @Transactional
    public Long create(DeptReq req) {
        if (deptMapper.selectCount(new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getDeptCode, req.deptCode)) > 0) {
            throw BizException.badRequest("部门编码已存在：" + req.deptCode);
        }
        SysDepartment dept = new SysDepartment();
        dept.setDeptCode(req.deptCode);
        dept.setDeptName(req.deptName);
        dept.setParentId(req.parentId);
        dept.setDeptType(req.deptType);
        dept.setLeaderId(req.leaderId);
        dept.setIsChainMaster(0);
        dept.setSortNo(req.sortNo);
        dept.setStatus(req.status == null ? "ACTIVE" : req.status);
        deptMapper.insert(dept);
        return dept.getId();
    }

    @Audited(module = "org", action = "update_dept", targetType = "sys_department", targetId = "{0}")
    @Transactional
    public void update(Long id, DeptReq req) {
        SysDepartment dept = deptMapper.selectById(id);
        if (dept == null) {
            throw BizException.notFound("部门不存在");
        }
        if (req.parentId != null && req.parentId.equals(id)) {
            throw BizException.badRequest("上级部门不能是自身");
        }
        dept.setDeptName(req.deptName);
        if (req.parentId != null) {
            dept.setParentId(req.parentId);
        }
        if (req.deptType != null) {
            dept.setDeptType(req.deptType);
        }
        if (req.leaderId != null) {
            dept.setLeaderId(req.leaderId);
        }
        if (req.sortNo != null) {
            dept.setSortNo(req.sortNo);
        }
        if (req.status != null) {
            dept.setStatus(req.status);
        }
        deptMapper.updateById(dept);
    }

    @Audited(module = "org", action = "delete_dept", targetType = "sys_department", targetId = "{0}")
    @Transactional
    public void delete(Long id) {
        long childCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getParentId, id));
        if (childCount > 0) {
            throw BizException.badRequest("存在下级部门，不可删除");
        }
        long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, id).eq(SysUser::getStatus, "ACTIVE"));
        if (userCount > 0) {
            throw BizException.badRequest("部门下存在在职用户，不可删除");
        }
        deptMapper.deleteById(id);
    }
}
