package com.projlife.crm.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.projlife.crm.entity.CrmOpportunity;
import com.projlife.security.datascope.DataScopeRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis-Plus Mapper：CrmOpportunity。
 * SPRINT-PRJ2（审计 F3）：R04 商务 data_scope=SELF → 商机列表/分页仅本人（owner_id）；
 * ALL/DEPT/PROJECT 角色不受影响（规则未声明 dept/project 列时对应条件不生成）。
 */
@Mapper
public interface CrmOpportunityMapper extends BaseMapper<CrmOpportunity> {

    @Override
    @DataScopeRule(selfIdColumn = "owner_id")
    List<CrmOpportunity> selectList(@Param(Constants.WRAPPER) Wrapper<CrmOpportunity> wrapper);

    @Override
    @DataScopeRule(selfIdColumn = "owner_id")
    <P extends IPage<CrmOpportunity>> P selectPage(P page, @Param(Constants.WRAPPER) Wrapper<CrmOpportunity> wrapper);
}
