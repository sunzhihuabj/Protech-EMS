package com.projlife.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.project.entity.PrjMember;
import com.projlife.project.mapper.PrjMemberMapper;
import com.projlife.security.auth.ProjectMembershipChecker;
import org.springframework.stereotype.Component;

/**
 * DataScopeGuard 项目成员 SPI 实现（SPRINT-C PROJECT 档落地）。
 * 独立成类而非塞进 ProjectService：守卫 → checker → ProjectService → 守卫 会成环。
 * 口径与列表层 AnnotationDataPermissionHandler 的 PROJECT 档子查询一致：
 * project_id + user_id + is_active=1（deleted 由 @TableLogic 自动过滤）。
 */
@Component
public class PrjMembershipProvider implements ProjectMembershipChecker {

    private final PrjMemberMapper memberMapper;

    public PrjMembershipProvider(PrjMemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    @Override
    public boolean isActiveMember(Long projectId, Long userId) {
        if (projectId == null || userId == null) {
            return false;
        }
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<PrjMember>()
                .eq(PrjMember::getProjectId, projectId)
                .eq(PrjMember::getUserId, userId)
                .eq(PrjMember::getIsActive, 1));
        return count != null && count > 0;
    }
}
