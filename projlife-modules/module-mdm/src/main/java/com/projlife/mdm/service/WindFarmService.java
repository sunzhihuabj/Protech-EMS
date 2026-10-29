package com.projlife.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.mdm.entity.MdWindFarm;
import com.projlife.mdm.mapper.MdWindFarmMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 风场建档（12 册 §2.3）。
 * FR-MDM-09 名称归一化；FR-MDM-10 一地一档（省+市+县+名称）；FR-MDM-11 期次提示。
 */
@Service
public class WindFarmService {

    private final MdWindFarmMapper farmMapper;
    private final CodeSequenceService codeSequenceService;
    private final NameRuleService nameRuleService;

    public WindFarmService(MdWindFarmMapper farmMapper, CodeSequenceService codeSequenceService,
                           NameRuleService nameRuleService) {
        this.farmMapper = farmMapper;
        this.codeSequenceService = codeSequenceService;
        this.nameRuleService = nameRuleService;
    }

    public MdWindFarm detail(Long id) {
        MdWindFarm farm = farmMapper.selectById(id);
        if (farm == null) {
            throw BizException.notFound("风场不存在");
        }
        return farm;
    }

    /** FR-MDM-10：一地一档查重（省+市+县+归一化名称） */
    public Long findByLocation(String province, String city, String county, String farmName) {
        String core = nameRuleService.stripSuffix(farmName);
        for (MdWindFarm farm : farmMapper.selectList(new LambdaQueryWrapper<MdWindFarm>()
                .eq(MdWindFarm::getProvince, province)
                .eq(MdWindFarm::getCity, city)
                .eq(MdWindFarm::getCounty, county))) {
            if (nameRuleService.stripSuffix(farm.getFarmName()).equals(core)) {
                return farm.getId();
            }
        }
        return null;
    }

    @Audited(module = "mdm", action = "create_farm", targetType = "md_wind_farm", targetId = "{0.farmName}")
    @Transactional
    public Long create(MdWindFarm farm) {
        if (farm.getFarmName() == null || farm.getFarmName().isEmpty()) {
            throw BizException.badRequest("风场名称必填");
        }
        Long dup = findByLocation(farm.getProvince(), farm.getCity(), farm.getCounty(), farm.getFarmName());
        if (dup != null) {
            throw BizException.badRequest("同省市区+名称的风场已存在（FR-MDM-10 一地一档），请关联已有档案");
        }
        farm.setFarmCode(codeSequenceService.next(CodeTypes.windFarm(
                String.valueOf(LocalDate.now().getYear()))));
        // FR-MDM-09：入库即归一化（去书名号/尾缀），原文由项目档案 farm_name_raw 保留
        farm.setFarmName(nameRuleService.stripSuffix(farm.getFarmName()));
        if (farm.getStatus() == null) {
            farm.setStatus("ACTIVE");
        }
        if (farm.getPhaseCount() == null) {
            farm.setPhaseCount(1);
        }
        farmMapper.insert(farm);
        return farm.getId();
    }
}
