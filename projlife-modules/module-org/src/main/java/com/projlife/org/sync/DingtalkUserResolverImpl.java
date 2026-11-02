package com.projlife.org.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.spi.DingtalkUserResolver;
import com.projlife.org.entity.SysDingtalkBinding;
import com.projlife.org.mapper.SysDingtalkBindingMapper;
import org.springframework.stereotype.Component;

/**
 * 钉钉 userid/unionId 解析器（SPRINT-INT2）：查 sys_dingtalk_binding 绑定表。
 * 通知适配器解析顺序（DingTalkNotifyAdapter）：yml 静态映射 → 本解析器 → 机器人兜底。
 */
@Component
public class DingtalkUserResolverImpl implements DingtalkUserResolver {

    private final SysDingtalkBindingMapper bindingMapper;

    public DingtalkUserResolverImpl(SysDingtalkBindingMapper bindingMapper) {
        this.bindingMapper = bindingMapper;
    }

    @Override
    public String resolveBySysUserId(Long sysUserId) {
        SysDingtalkBinding binding = selectBySysUserId(sysUserId);
        return binding == null ? null : binding.getDingtalkUserid();
    }

    @Override
    public String resolveUnionIdBySysUserId(Long sysUserId) {
        SysDingtalkBinding binding = selectBySysUserId(sysUserId);
        return binding == null ? null : binding.getUnionId();
    }

    private SysDingtalkBinding selectBySysUserId(Long sysUserId) {
        if (sysUserId == null) {
            return null;
        }
        return bindingMapper.selectOne(new LambdaQueryWrapper<SysDingtalkBinding>()
                .eq(SysDingtalkBinding::getSysUserId, sysUserId)
                .eq(SysDingtalkBinding::getDeleted, 0)
                .last("LIMIT 1"));
    }
}
