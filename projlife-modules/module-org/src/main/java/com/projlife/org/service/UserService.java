package com.projlife.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.common.util.MaskUtils;
import com.projlife.org.dto.UserCreateReq;
import com.projlife.org.dto.UserResp;
import com.projlife.org.dto.UserUpdateReq;
import com.projlife.org.entity.SysRole;
import com.projlife.org.entity.SysUser;
import com.projlife.org.entity.SysUserRole;
import com.projlife.org.mapper.SysRoleMapper;
import com.projlife.org.mapper.SysUserMapper;
import com.projlife.org.mapper.SysUserRoleMapper;
import com.projlife.org.mapper.UserPageQuery;
import com.projlife.org.service.audit.Audited;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理（S0-3/S0-4）。
 * FR：新增/编辑/冻结/重置密码；user_code 与 username 唯一；密码 BCrypt；手机号脱敏展示（02 册 §4.1）。
 */
@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OrgUserDetailsService contextService;

    public UserService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper,
                       PasswordEncoder passwordEncoder, OrgUserDetailsService contextService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.contextService = contextService;
    }

    public PageResp<UserResp> page(PageReq pageReq, UserPageQuery query) {
        Page<SysUser> page = userMapper.selectUserPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), query);
        List<UserResp> rows = new ArrayList<>();
        for (SysUser user : page.getRecords()) {
            rows.add(toResp(user));
        }
        PageResp<UserResp> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(rows);
        return resp;
    }

    public UserResp detail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return toResp(user);
    }

    @Audited(module = "org", action = "create_user", targetType = "sys_user", targetId = "{0.userCode}")
    @Transactional
    public Long create(UserCreateReq req) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.username)) > 0) {
            throw BizException.badRequest("登录名已存在：" + req.username);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserCode, req.userCode)) > 0) {
            throw BizException.badRequest("工号已存在：" + req.userCode);
        }
        SysUser user = new SysUser();
        user.setUserCode(req.userCode);
        user.setUsername(req.username);
        user.setRealName(req.realName);
        user.setPasswordHash(passwordEncoder.encode(req.password));
        user.setPhone(req.phone);
        user.setDeptId(req.deptId);
        user.setPosition(req.position);
        user.setUserType(req.userType);
        // 缺省法人 = 1（PRT 本部，00 册 §7.3 多法人约定；建档管理在 S1 主数据切片完善）
        user.setLegalEntityId(req.legalEntityId == null ? 1L : req.legalEntityId);
        user.setStatus("ACTIVE");
        user.setMustChangePwd(1);
        user.setPwdUpdatedAt(LocalDateTime.now());
        user.setRemark(req.remark);
        userMapper.insert(user);
        assignRoles(user.getId(), req.roleIds);
        return user.getId();
    }

    @Audited(module = "org", action = "update_user", targetType = "sys_user", targetId = "{0}")
    @Transactional
    public void update(Long id, UserUpdateReq req) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (req.realName != null) {
            user.setRealName(req.realName);
        }
        if (req.phone != null) {
            user.setPhone(req.phone);
        }
        if (req.deptId != null) {
            user.setDeptId(req.deptId);
        }
        if (req.position != null) {
            user.setPosition(req.position);
        }
        if (req.userType != null) {
            user.setUserType(req.userType);
        }
        if (req.legalEntityId != null) {
            user.setLegalEntityId(req.legalEntityId);
        }
        if (req.status != null) {
            user.setStatus(req.status);
        }
        if (req.remark != null) {
            user.setRemark(req.remark);
        }
        userMapper.updateById(user);
        if (req.roleIds != null) {
            assignRoles(id, req.roleIds);
        }
        contextService.evict(id);
    }

    /** 冻结/解冻（E-02）。冻结后 JwtAuthFilter 实时加载上下文将返回 401（UC-SYS-15） */
    @Audited(module = "org", action = "freeze_user", targetType = "sys_user", targetId = "{0}")
    @Transactional
    public void changeStatus(Long id, String status) {
        if (!"ACTIVE".equals(status) && !"FROZEN".equals(status) && !"PENDING".equals(status)) {
            throw BizException.badRequest("非法状态：" + status);
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        contextService.evict(id);
    }

    @Audited(module = "org", action = "reset_password", targetType = "sys_user", targetId = "{0}")
    @Transactional
    public void resetPassword(Long id, String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePwd(1);
        user.setPwdUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        contextService.evict(id);
    }

    private void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            if (roleMapper.selectById(roleId) == null) {
                throw BizException.badRequest("角色不存在：" + roleId);
            }
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
        contextService.evict(userId);
    }

    private UserResp toResp(SysUser user) {
        UserResp resp = new UserResp();
        resp.id = user.getId();
        resp.userCode = user.getUserCode();
        resp.username = user.getUsername();
        resp.realName = user.getRealName();
        resp.phone = MaskUtils.maskPhone(user.getPhone());
        resp.deptId = user.getDeptId();
        resp.position = user.getPosition();
        resp.userType = user.getUserType();
        resp.legalEntityId = user.getLegalEntityId();
        resp.status = user.getStatus();
        resp.mustChangePwd = user.getMustChangePwd() != null && user.getMustChangePwd() == 1;
        resp.themeCode = user.getThemeCode();
        resp.lastLoginAt = user.getLastLoginAt();
        resp.remark = user.getRemark();

        List<SysUserRole> relations = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, user.getId()));
        List<Long> roleIds = new ArrayList<>();
        List<String> roleNames = new ArrayList<>();
        for (SysUserRole relation : relations) {
            SysRole role = roleMapper.selectById(relation.getRoleId());
            if (role != null) {
                roleIds.add(role.getId());
                roleNames.add(role.getRoleName());
            }
        }
        resp.roleIds = roleIds;
        resp.roleNames = roleNames;
        return resp;
    }
}
