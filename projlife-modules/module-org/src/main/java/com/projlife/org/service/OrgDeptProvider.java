package com.projlife.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.org.entity.SysDepartment;
import com.projlife.org.mapper.SysDepartmentMapper;
import com.projlife.security.auth.DeptTreeProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门树查询（DEPT_SUB 数据范围用）。
 */
@Service
public class OrgDeptProvider implements DeptTreeProvider {

    private final SysDepartmentMapper deptMapper;

    public OrgDeptProvider(SysDepartmentMapper deptMapper) {
        this.deptMapper = deptMapper;
    }

    @Override
    public List<Long> selfAndSubDeptIds(Long deptId) {
        List<Long> result = new ArrayList<>();
        if (deptId == null) {
            return result;
        }
        List<SysDepartment> all = deptMapper.selectList(new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getStatus, "ACTIVE"));
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (SysDepartment dept : all) {
            childrenMap.computeIfAbsent(dept.getParentId() == null ? 0L : dept.getParentId(),
                    k -> new ArrayList<>()).add(dept.getId());
        }
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(deptId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (result.contains(current)) {
                continue;
            }
            result.add(current);
            List<Long> children = childrenMap.get(current);
            if (children != null) {
                children.forEach(stack::push);
            }
        }
        return result;
    }
}
