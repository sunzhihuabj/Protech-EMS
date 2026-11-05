package com.projlife.security.auth;

import com.projlife.common.context.LoginUser;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.enums.DataScope;
import com.projlife.common.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 按 ID 直查的数据范围守卫（SPRINT-SEC3）。
 * 列表查询已有数据范围过滤；按 ID 直查（如 GET /contracts/{id}）此前绕过数据范围，
 * SELF/DEPT 档用户可看任意单据——本守卫按单据创建人（created_by 工号）补齐校验：
 * <ul>
 *   <li>ALL：放行</li>
 *   <li>SELF：owner == 本人工号</li>
 *   <li>DEPT / DEPT_SUB：owner 部门 == 本人部门（DEPT_SUB 暂按本部门，部门树联动待 ENG 模块开放后补）</li>
 *   <li>PROJECT：暂放行——项目成员维度（prj_member）暂无数据，按 owner 部门比对反而破坏 R14 工程跨项目协作，备案待补</li>
 *   <li>owner 为 system / 工号查无此人：放行（系统动作数据，备案）</li>
 * </ul>
 * 不控制字段脱敏（BR-06 等由各端点独立判断）。
 */
@Component
public class DataScopeGuard {

    private final UserDetailsProvider userDetailsProvider;

    public DataScopeGuard(UserDetailsProvider userDetailsProvider) {
        this.userDetailsProvider = userDetailsProvider;
    }

    /** 按创建人（工号）校验当前登录用户的数据范围，越界抛 403 */
    public void checkByOwner(String ownerUserCode) {
        LoginUser me = UserContextHolder.get();
        if (me == null) {
            throw BizException.of(401, "未登录或会话过期");
        }
        if (DataScope.ALL == DataScope.of(me.getDataScope())) {
            return;
        }
        if (ownerUserCode == null || ownerUserCode.isEmpty() || "system".equals(ownerUserCode)) {
            return; // 系统动作创建的数据不按人限制
        }
        switch (DataScope.of(me.getDataScope())) {
            case SELF:
                requireSelf(me, ownerUserCode);
                return;
            case DEPT:
            case DEPT_SUB:
                requireSameDept(me, ownerUserCode);
                return;
            case PROJECT:
            case ALL:
            default:
                // PROJECT/ALL 放行（见类注释备案）
        }
    }

    private void requireSelf(LoginUser me, String ownerUserCode) {
        if (!ownerUserCode.equals(me.getUserCode())) {
            throw forbidden();
        }
    }

    private void requireSameDept(LoginUser me, String ownerUserCode) {
        if (me.getDeptId() == null) {
            return; // 本人无部门归属（如第三方账号），按宽口径放行，备案
        }
        LoginUser owner = userDetailsProvider.loadByUserCode(ownerUserCode);
        if (owner == null || owner.getDeptId() == null) {
            return; // 工号查无此人（历史数据/系统账号），放行
        }
        if (!owner.getDeptId().equals(me.getDeptId())) {
            throw forbidden();
        }
    }

    private BizException forbidden() {
        return BizException.forbidden("无权限查看该数据（数据范围限制）");
    }
}
