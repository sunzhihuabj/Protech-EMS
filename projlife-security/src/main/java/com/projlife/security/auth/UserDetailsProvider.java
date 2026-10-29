package com.projlife.security.auth;

import com.projlife.common.context.LoginUser;

/**
 * 登录用户信息加载接口（安全层定义，组织模块实现）。
 * 实现方负责角色/权限码装配与 30 分钟缓存（03 册 §1.2 判定顺序）。
 */
public interface UserDetailsProvider {

    /** 按登录名加载（含冻结校验）；不存在/不可用返回 null */
    LoginUser loadByUsername(String username);

    /** 按 userId 加载 */
    LoginUser loadById(Long userId);
}
