package com.projlife.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.context.LoginUser;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.common.kv.KeyValStore;
import com.projlife.org.dto.ChangePasswordReq;
import com.projlife.org.dto.LoginReq;
import com.projlife.org.dto.LoginResp;
import com.projlife.org.dto.MenuNode;
import com.projlife.org.dto.ThemeReq;
import com.projlife.org.entity.SysPermission;
import com.projlife.org.entity.SysUser;
import com.projlife.org.mapper.SysPermissionMapper;
import com.projlife.org.mapper.SysUserMapper;
import com.projlife.security.auth.LoginProtectionService;
import com.projlife.security.auth.UserDetailsProvider;
import com.projlife.security.config.SecurityProperties;
import com.projlife.security.jwt.JwtUtil;
import com.projlife.security.token.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务（S0-3）：登录/登出/刷新/改密/主题。
 *
 * FR 清单：登录失败锁定（5 次 15 分钟）、BR-08 端限制前置（WORKER 仅小程序）、
 * 首登强制改密、JWT access 2h + refresh 7d、登出黑名单、个人主题持久化（03 册 §6A.1）。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 端限制：施工人员仅允许小程序端（BR-08；正式范围 S7 生效，登录行为自 S0 起拦截） */
    private static final String CLIENT_MP = "mp";

    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final UserDetailsProvider userDetailsProvider;
    private final OrgUserDetailsService orgUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklist;
    private final LoginProtectionService loginProtection;
    private final SecurityProperties securityProps;
    private final KeyValStore cache;

    public AuthService(SysUserMapper userMapper, SysPermissionMapper permissionMapper,
                       UserDetailsProvider userDetailsProvider, OrgUserDetailsService orgUserDetailsService,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil, TokenBlacklistService blacklist,
                       LoginProtectionService loginProtection, SecurityProperties securityProps, KeyValStore cache) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.userDetailsProvider = userDetailsProvider;
        this.orgUserDetailsService = orgUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.blacklist = blacklist;
        this.loginProtection = loginProtection;
        this.securityProps = securityProps;
        this.cache = cache;
    }

    @Transactional
    public LoginResp login(LoginReq req, String ip) {
        loginProtection.checkLocked(req.username);

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.username));
        if (user == null || user.getDeleted() != null && user.getDeleted() == 1) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "登录名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw BizException.of(ErrorCode.FORBIDDEN, "账号已停用，请联系管理员");
        }
        if (isExpiredThirdParty(user)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "第三方账号已过有效期");
        }
        enforceClientLimit(user, req.client);

        if (!passwordEncoder.matches(req.password, user.getPasswordHash())) {
            boolean locked = loginProtection.recordFail(req.username);
            log.warn("登录失败 username={} locked={}", req.username, locked);
            throw BizException.of(ErrorCode.PARAM_INVALID, "登录名或密码错误");
        }

        loginProtection.clear(req.username);
        String access = jwtUtil.generateAccess(user.getId(), user.getUserCode(), user.getUsername(), user.getUserType());
        String refresh = jwtUtil.generateRefresh(user.getId(), user.getUserCode(), user.getUsername(), user.getUserType());

        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        userMapper.updateById(user);

        LoginResp resp = new LoginResp();
        resp.accessToken = access;
        resp.refreshToken = refresh;
        resp.expiresIn = securityProps.getAccessTtlSeconds();
        resp.mustChangePwd = user.getMustChangePwd() != null && user.getMustChangePwd() == 1;
        resp.userId = user.getId();
        resp.userCode = user.getUserCode();
        resp.realName = user.getRealName();
        resp.themeCode = user.getThemeCode();
        log.info("登录成功 username={} ip={}", req.username, ip);
        return resp;
    }

    /** BR-08：第三方施工人员只能登录小程序，PC 端拒绝（403） */
    private void enforceClientLimit(SysUser user, String client) {
        if ("WORKER".equals(user.getUserType()) && !CLIENT_MP.equalsIgnoreCase(client)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "施工人员账号仅允许微信小程序登录");
        }
    }

    private boolean isExpiredThirdParty(SysUser user) {
        if (!"INTERNAL".equals(user.getUserType())) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if (user.getValidFrom() != null && user.getValidFrom().isAfter(today)) {
            return true;
        }
        return user.getValidTo() != null && user.getValidTo().isBefore(today);
    }

    public void logout(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return;
        }
        Claims claims = jwtUtil.parse(bearerToken.substring(7));
        blacklist.revoke(claims);
    }

    public LoginResp refresh(String refreshToken) {
        Claims claims = jwtUtil.parse(refreshToken);
        if (blacklist.isRevoked(claims)) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "刷新令牌已失效");
        }
        if (!JwtUtil.TYPE_REFRESH.equals(claims.get("typ"))) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "非法的刷新令牌");
        }
        SysUser user = userMapper.selectById(Long.valueOf((String) claims.get("uid")));
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "账号不可用");
        }
        LoginResp resp = new LoginResp();
        resp.accessToken = jwtUtil.generateAccess(user.getId(), user.getUserCode(), user.getUsername(), user.getUserType());
        resp.refreshToken = refreshToken;
        resp.expiresIn = securityProps.getAccessTtlSeconds();
        resp.mustChangePwd = user.getMustChangePwd() != null && user.getMustChangePwd() == 1;
        resp.userId = user.getId();
        resp.userCode = user.getUserCode();
        resp.realName = user.getRealName();
        resp.themeCode = user.getThemeCode();
        return resp;
    }

    /** 修改密码：校验旧密码 → BCrypt 存储 → 作废当前会话（16 册 §4.4） */
    @Transactional
    public void changePassword(Long userId, ChangePasswordReq req, String currentToken) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        if (!passwordEncoder.matches(req.oldPassword, user.getPasswordHash())) {
            throw BizException.badRequest("原密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword));
        user.setMustChangePwd(0);
        user.setPwdUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        if (currentToken != null && currentToken.startsWith("Bearer ")) {
            blacklist.revoke(jwtUtil.parse(currentToken.substring(7)));
        }
        orgUserDetailsService.evict(userId);
        log.info("密码修改成功 userId={}", userId);
    }

    /** 个人主题持久化（03 册 §6A.1：覆盖系统默认；多端一致） */
    @Transactional
    public void saveTheme(Long userId, ThemeReq req) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        user.setThemeCode(req.themeCode);
        userMapper.updateById(user);
        orgUserDetailsService.evict(userId);
    }

    /** 当前用户全量信息：身份 + 权限码 + 菜单树（S0-14 前端按权限渲染菜单的数据源） */
    public Map<String, Object> me(Long userId) {
        LoginUser loginUser = userDetailsProvider.loadById(userId);
        if (loginUser == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "账号不可用");
        }
        SysUser user = userMapper.selectById(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", loginUser.getUserId());
        result.put("userCode", loginUser.getUserCode());
        result.put("username", loginUser.getUsername());
        result.put("realName", loginUser.getRealName());
        result.put("deptId", loginUser.getDeptId());
        result.put("dataScope", loginUser.getDataScope());
        result.put("userType", loginUser.getUserType());
        result.put("themeCode", user == null ? null : user.getThemeCode());
        result.put("permissions", loginUser.getPermissions());
        result.put("menus", buildMenus(loginUser.getPermissions()));
        return result;
    }

    /** MENU 型权限树；仅保留用户已授权的菜单（按权限渲染菜单，未授权不出现） */
    private List<MenuNode> buildMenus(java.util.Set<String> permCodes) {
        List<SysPermission> allMenus = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermType, "MENU")
                .eq(SysPermission::getStatus, "ACTIVE"));
        List<SysPermission> granted = new ArrayList<>();
        for (SysPermission p : allMenus) {
            if (permCodes.contains(p.getPermCode())) {
                granted.add(p);
            }
        }
        granted.sort(Comparator.comparing(SysPermission::getSortNo, Comparator.nullsLast(Comparator.naturalOrder())));
        Map<Long, MenuNode> rowMap = new HashMap<>();
        for (SysPermission p : granted) {
            rowMap.put(p.getId(), new MenuNode(p.getPermCode(), p.getPermName(), p.getSortNo()));
        }
        List<MenuNode> roots = new ArrayList<>();
        for (SysPermission p : granted) {
            MenuNode node = rowMap.get(p.getId());
            MenuNode parent = p.getParentId() == null ? null : rowMap.get(p.getParentId());
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        return roots;
    }
}
