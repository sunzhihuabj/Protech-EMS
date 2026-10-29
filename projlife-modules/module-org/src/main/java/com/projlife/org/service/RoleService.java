package com.projlife.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.org.dto.RoleReq;
import com.projlife.org.entity.SysPermission;
import com.projlife.org.entity.SysRole;
import com.projlife.org.entity.SysRolePermission;
import com.projlife.org.entity.SysUserRole;
import com.projlife.org.mapper.SysPermissionMapper;
import com.projlife.org.mapper.SysRoleMapper;
import com.projlife.org.mapper.SysRolePermissionMapper;
import com.projlife.org.mapper.SysUserRoleMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.common.enums.DataScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色与权限分配（S0-4）。权限变更后失效相关用户上下文缓存。
 */
@Service
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final OrgUserDetailsService contextService;

    public RoleService(SysRoleMapper roleMapper, SysRolePermissionMapper rolePermissionMapper,
                       SysPermissionMapper permissionMapper, SysUserRoleMapper userRoleMapper,
                       OrgUserDetailsService contextService) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.contextService = contextService;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysRole role : roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId))) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", role.getId());
            row.put("roleCode", role.getRoleCode());
            row.put("roleName", role.getRoleName());
            row.put("dataScope", role.getDataScope());
            row.put("status", role.getStatus());
            row.put("remark", role.getRemark());
            row.put("permIds", findPermIds(role.getId()));
            rows.add(row);
        }
        return rows;
    }

    /** 权限码树（分配界面数据源） */
    public List<Map<String, Object>> permissionTree() {
        List<SysPermission> all = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSortNo));
        Map<Long, Map<String, Object>> nodeMap = new HashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (SysPermission p : all) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", p.getId());
            node.put("permCode", p.getPermCode());
            node.put("permName", p.getPermName());
            node.put("module", p.getModule());
            node.put("permType", p.getPermType());
            node.put("parentId", p.getParentId());
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(p.getId(), node);
        }
        for (SysPermission p : all) {
            Map<String, Object> node = nodeMap.get(p.getId());
            Map<String, Object> parent = p.getParentId() == null ? null : nodeMap.get(p.getParentId());
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

    @Audited(module = "org", action = "create_role", targetType = "sys_role", targetId = "{0.roleCode}")
    @Transactional
    public Long create(RoleReq req) {
        if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, req.roleCode)) > 0) {
            throw BizException.badRequest("角色编码已存在：" + req.roleCode);
        }
        validateDataScope(req.dataScope);
        SysRole role = new SysRole();
        role.setRoleCode(req.roleCode);
        role.setRoleName(req.roleName);
        role.setDataScope(req.dataScope);
        role.setRemark(req.remark);
        role.setStatus(req.status == null ? "ACTIVE" : req.status);
        roleMapper.insert(role);
        replacePermissions(role.getId(), req.permIds);
        return role.getId();
    }

    @Audited(module = "org", action = "update_role", targetType = "sys_role", targetId = "{0}")
    @Transactional
    public void update(Long id, RoleReq req) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BizException.notFound("角色不存在");
        }
        validateDataScope(req.dataScope);
        role.setRoleName(req.roleName);
        role.setDataScope(req.dataScope);
        role.setRemark(req.remark);
        if (req.status != null) {
            role.setStatus(req.status);
        }
        roleMapper.updateById(role);
        replacePermissions(id, req.permIds);
    }

    private void validateDataScope(String dataScope) {
        try {
            DataScope.valueOf(dataScope);
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("非法数据范围：" + dataScope);
        }
    }

    private void replacePermissions(Long roleId, List<Long> permIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        if (permIds == null) {
            return;
        }
        Set<Long> distinct = new HashSet<>(permIds);
        for (Long permId : distinct) {
            if (permissionMapper.selectById(permId) == null) {
                throw BizException.badRequest("权限项不存在：" + permId);
            }
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermId(permId);
            rolePermissionMapper.insert(relation);
        }
        evictRoleUsers(roleId);
    }

    private void evictRoleUsers(Long roleId) {
        List<SysUserRole> relations = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        contextService.evictAll(relations.stream().map(SysUserRole::getUserId).collect(java.util.stream.Collectors.toList()));
    }

    private List<Long> findPermIds(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermId).collect(java.util.stream.Collectors.toList());
    }
}
