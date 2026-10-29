package com.projlife.org.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.org.entity.SysConfig;
import com.projlife.org.mapper.SysConfigMapper;
import com.projlife.org.service.audit.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统参数（S0-17：app.theme.default 由 R03 配置）。
 */
@Service
public class ConfigService {

    private final SysConfigMapper configMapper;

    public ConfigService(SysConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public String getValue(String key) {
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        return config == null ? null : config.getConfigValue();
    }

    public List<Map<String, Object>> list(String group) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<SysConfig>()
                .orderByAsc(SysConfig::getConfigGroup).orderByAsc(SysConfig::getConfigKey);
        if (group != null && !group.isEmpty()) {
            wrapper.eq(SysConfig::getConfigGroup, group);
        }
        return configMapper.selectList(wrapper).stream().map(this::toMap).collect(Collectors.toList());
    }

    @Audited(module = "system", action = "save_config", targetType = "sys_config", targetId = "{0.configKey}")
    @Transactional
    public void save(String key, String value, String remark) {
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        if (config == null) {
            throw BizException.notFound("参数不存在：" + key + "（禁止随意新增参数，需先在种子中登记）");
        }
        config.setConfigValue(value);
        if (remark != null) {
            config.setRemark(remark);
        }
        configMapper.updateById(config);
    }

    private Map<String, Object> toMap(SysConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", config.getId());
        map.put("configKey", config.getConfigKey());
        map.put("configValue", config.getConfigValue());
        map.put("configGroup", config.getConfigGroup());
        map.put("remark", config.getRemark());
        return map;
    }
}
