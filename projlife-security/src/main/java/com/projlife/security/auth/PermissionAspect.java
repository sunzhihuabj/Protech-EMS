package com.projlife.security.auth;

import com.projlife.common.context.LoginUser;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 权限切面：@RequirePermission 声明式校验（03 册 §1.2）。
 * 判定顺序：JWT → 权限码集合（含内置权限）→ 未命中 403。
 */
@Aspect
@Component
public class PermissionAspect {

    @Before("@annotation(requirePermission)")
    public void check(JoinPoint joinPoint, RequirePermission requirePermission) {
        String permCode = requirePermission.value();
        if (BuiltinPermissions.isBuiltin(permCode)) {
            return;
        }
        LoginUser user = UserContextHolder.get();
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED, "未登录");
        }
        if (!user.hasPermission(permCode)) {
            throw BizException.of(ErrorCode.FORBIDDEN, "无权限执行该操作").with("missingPerm", permCode);
        }
    }
}
