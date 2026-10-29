package com.projlife.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projlife.common.context.LoginUser;
import com.projlife.common.enums.DataScope;
import com.projlife.common.kv.KeyValStore;
import com.projlife.org.entity.SysPermission;
import com.projlife.org.entity.SysRole;
import com.projlife.org.entity.SysRolePermission;
import com.projlife.org.entity.SysUser;
import com.projlife.org.entity.SysUserRole;
import com.projlife.org.mapper.SysPermissionMapper;
import com.projlife.org.mapper.SysRoleMapper;
import com.projlife.org.mapper.SysRolePermissionMapper;
import com.projlife.org.mapper.SysUserMapper;
import com.projlife.org.mapper.SysUserRoleMapper;
import com.projlife.security.auth.UserDetailsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录用户信息装配（03 册 §1.2 判定顺序）：角色并集取最宽数据范围；权限码 Redis 缓存 30 分钟。
 */
@Service
public class OrgUserDetailsService implements UserDetailsProvider {

    private static final Logger log = LoggerFactory.getLogger(OrgUserDetailsService.class);

    private static final String CACHE_PREFIX = "user:ctx:";
    private static final long CACHE_TTL_SECONDS = 1800;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final KeyValStore cache;
    private final ObjectMapper objectMapper;

    public OrgUserDetailsService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                                 SysRoleMapper roleMapper, SysRolePermissionMapper rolePermissionMapper,
                                 SysPermissionMapper permissionMapper, KeyValStore cache, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    @Override
    public LoginUser loadByUsername(String username) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        return user == null ? null : buildLoginUser(user);
    }

    @Override
    public LoginUser loadById(Long userId) {
        LoginUser cached = readCache(userId);
        if (cached != null) {
            return cached;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        LoginUser loginUser = buildLoginUser(user);
        writeCache(userId, loginUser);
        return loginUser;
    }

    /** 角色/权限变更后调用，立即失效缓存 */
    public void evict(Long userId) {
        cache.remove(CACHE_PREFIX + userId);
    }

    public void evictAll(List<Long> userIds) {
        if (userIds == null) {
            return;
        }
        userIds.forEach(this::evict);
    }

    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUserCode(user.getUserCode());
        loginUser.setUsername(user.getUsername());
        loginUser.setRealName(user.getRealName());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setUserType(user.getUserType());
        loginUser.setPhone(user.getPhone());

        List<SysRole> roles = findActiveRoles(user.getId());
        loginUser.setRoleCodes(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toSet()));
        loginUser.setDataScope(widestScope(roles).name());
        loginUser.setPermissions(loadPermissions(roles));
        return loginUser;
    }

    private List<SysRole> findActiveRoles(Long userId) {
        List<SysUserRole> relations = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (relations.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> roleIds = relations.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, "ACTIVE"));
    }

    /** 角色并集中取最宽数据范围（03 册 §1.1：数据范围档位取宽） */
    private DataScope widestScope(List<SysRole> roles) {
        DataScope widest = DataScope.SELF;
        for (SysRole role : roles) {
            widest = DataScope.wider(widest, DataScope.of(role.getDataScope()));
        }
        return widest;
    }

    private Set<String> loadPermissions(List<SysRole> roles) {
        if (roles.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> roleIds = roles.stream().map(SysRole::getId).collect(Collectors.toList());
        List<SysRolePermission> relations = rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                .in(SysRolePermission::getRoleId, roleIds));
        if (relations.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> permIds = relations.stream().map(SysRolePermission::getPermId).distinct().collect(Collectors.toList());
        List<SysPermission> perms = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .in(SysPermission::getId, permIds)
                .eq(SysPermission::getStatus, "ACTIVE"));
        return perms.stream().map(SysPermission::getPermCode).collect(Collectors.toSet());
    }

    private LoginUser readCache(Long userId) {
        try {
            String json = cache.get(CACHE_PREFIX + userId);
            return json == null ? null : objectMapper.readValue(json, LoginUser.class);
        } catch (Exception ex) {
            log.warn("用户上下文缓存读取失败，回源查询 userId={}", userId, ex);
            return null;
        }
    }

    private void writeCache(Long userId, LoginUser user) {
        try {
            cache.put(CACHE_PREFIX + userId, objectMapper.writeValueAsString(user), CACHE_TTL_SECONDS);
        } catch (Exception ex) {
            log.warn("用户上下文缓存写入失败 userId={}", userId, ex);
        }
    }
}
