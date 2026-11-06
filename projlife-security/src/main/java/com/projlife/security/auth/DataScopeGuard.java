package com.projlife.security.auth;

import com.projlife.common.context.LoginUser;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.enums.DataScope;
import com.projlife.common.exception.BizException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 按 ID 直查的数据范围守卫（SPRINT-SEC3，SPRINT-C 增强）。
 * 列表查询已有数据范围过滤；按 ID 直查（如 GET /contracts/{id}）此前绕过数据范围。
 * <ul>
 *   <li>ALL：放行</li>
 *   <li>SELF：owner == 本人工号</li>
 *   <li>DEPT / DEPT_SUB：owner 部门 == 本人部门（DEPT_SUB 暂按本部门，部门树联动待 ENG 模块开放后补）</li>
 *   <li>owner 为 system / 工号查无此人：放行（系统动作数据，备案）</li>
 * </ul>
 * 项目维度校验见 {@link #checkByProject(Long, String)}（PROJECT 档落地 + 成员兜底）。
 * 不控制字段脱敏（BR-06 等由各端点独立判断）。
 */
@Component
public class DataScopeGuard {

    private final UserDetailsProvider userDetailsProvider;

    /** 项目成员查询 SPI：module-project 提供；无实现时 PROJECT 档退回备案放行 */
    private final ObjectProvider<ProjectMembershipChecker> membershipChecker;

    public DataScopeGuard(UserDetailsProvider userDetailsProvider,
                          ObjectProvider<ProjectMembershipChecker> membershipChecker) {
        this.userDetailsProvider = userDetailsProvider;
        this.membershipChecker = membershipChecker;
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
                // PROJECT/ALL 放行（owner 口径不适用项目域，项目维度走 checkByProject）
        }
    }

    /**
     * 项目维度校验（SPRINT-C）：
     * <ul>
     *   <li>ALL：放行</li>
     *   <li>PROJECT：须为项目在册成员（prj_member，与列表层 SQL 过滤同口径；无 SPI 实现时备案放行）</li>
     *   <li>SELF / DEPT / DEPT_SUB：先按 owner 工号口径，被拒时若为项目在册成员则放行
     *       （FR-PRJ-28/29：跨部门被拉进项目协作即可见，兜底通道）</li>
     *   <li>owner 为 system / null：放行（备案）</li>
     * </ul>
     */
    public void checkByProject(Long projectId, String ownerUserCode) {
        LoginUser me = UserContextHolder.get();
        if (me == null) {
            throw BizException.of(401, "未登录或会话过期");
        }
        if (DataScope.ALL == DataScope.of(me.getDataScope())) {
            return;
        }
        if (ownerUserCode == null || ownerUserCode.isEmpty() || "system".equals(ownerUserCode)) {
            return;
        }
        ProjectMembershipChecker checker = membershipChecker.getIfAvailable();
        if (DataScope.PROJECT == DataScope.of(me.getDataScope())) {
            if (checker != null && !checker.isActiveMember(projectId, me.getUserId())) {
                throw forbidden();
            }
            return; // 无 SPI 实现时备案放行
        }
        try {
            if (DataScope.SELF == DataScope.of(me.getDataScope())) {
                requireSelf(me, ownerUserCode);
            } else {
                requireSameDept(me, ownerUserCode);
            }
        } catch (BizException ex) {
            // 成员兜底：owner 口径被拒，但在册成员视为可见（协作成员通道）
            if (checker == null || projectId == null || !checker.isActiveMember(projectId, me.getUserId())) {
                throw ex;
            }
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
