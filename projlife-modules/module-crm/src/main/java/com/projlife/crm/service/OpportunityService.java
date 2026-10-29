package com.projlife.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.crm.entity.CrmOpportunity;
import com.projlife.crm.entity.CrmOpportunityFollowup;
import com.projlife.crm.entity.CrmSalesFunnel;
import com.projlife.crm.mapper.CrmOpportunityFollowupMapper;
import com.projlife.crm.mapper.CrmOpportunityMapper;
import com.projlife.crm.mapper.CrmSalesFunnelMapper;
import com.projlife.mdm.api.MdmApi;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商机全流程（04 册 §2：FR-CRM-01~09）。
 * BR-CRM-01 任何状态不物理删除；FR-CRM-02 新建即跟踪中、创建人为负责人；
 * FR-CRM-06 转合同；FR-CRM-07 丢单必填原因；FR-CRM-08 重激活需 R05/R01（权限层控制）。
 */
@Service
public class OpportunityService {

    private final CrmOpportunityMapper opportunityMapper;
    private final CrmOpportunityFollowupMapper followupMapper;
    private final CrmSalesFunnelMapper funnelMapper;
    private final CodeSequenceService codeSequenceService;
    private final MdmApi mdmApi;

    public OpportunityService(CrmOpportunityMapper opportunityMapper, CrmOpportunityFollowupMapper followupMapper,
                              CrmSalesFunnelMapper funnelMapper, CodeSequenceService codeSequenceService,
                              MdmApi mdmApi) {
        this.opportunityMapper = opportunityMapper;
        this.followupMapper = followupMapper;
        this.funnelMapper = funnelMapper;
        this.codeSequenceService = codeSequenceService;
        this.mdmApi = mdmApi;
    }

    public PageResp<CrmOpportunity> page(PageReq pageReq, String keyword, String status,
                                         String intentionLevel, Long ownerId) {
        LambdaQueryWrapper<CrmOpportunity> wrapper = new LambdaQueryWrapper<CrmOpportunity>()
                .orderByDesc(CrmOpportunity::getId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(CrmOpportunity::getOppName, keyword);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(CrmOpportunity::getStatus, status);
        }
        if (intentionLevel != null && !intentionLevel.isEmpty()) {
            wrapper.eq(CrmOpportunity::getIntentionLevel, intentionLevel);
        }
        if (ownerId != null) {
            wrapper.eq(CrmOpportunity::getOwnerId, ownerId);
        }
        Page<CrmOpportunity> page = opportunityMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<CrmOpportunity> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    public CrmOpportunity detail(Long id) {
        return mustGet(id);
    }

    /** FR-CRM-01/02：建档（编号 OPP-{YYYYMM}-{4位}，状态跟踪中，负责人=创建人） */
    @Audited(module = "opportunity", action = "create_opportunity", targetType = "crm_opportunity", targetId = "{0.oppName}")
    @Transactional
    public Long create(CrmOpportunity opp) {
        if (opp.getOppName() == null || opp.getOppName().isEmpty()) {
            throw BizException.badRequest("商机名称必填");
        }
        if (opp.getCustomerId() == null || !mdmApi.customerActive(opp.getCustomerId())) {
            throw BizException.badRequest("客户主体不存在或已停用，请从主体库选择（FR-CRM-01）");
        }
        opp.setOppNo(codeSequenceService.next(CodeTypes.opportunity(
                LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")))));
        opp.setStatus("TRACKING");
        if (opp.getOwnerId() == null) {
            opp.setOwnerId(UserContextHolder.currentUserId());
        }
        if (opp.getIntentionLevel() == null) {
            opp.setIntentionLevel("M");
        }
        opportunityMapper.insert(opp);
        // 漏斗：进入 OPPORTUNITY 阶段
        enterFunnel(opp.getId(), "OPPORTUNITY", opp.getExpectAmount());
        return opp.getId();
    }

    /** FR-CRM-03：跟进时间线 */
    @Transactional
    public Long addFollowup(CrmOpportunityFollowup followup) {
        mustGet(followup.getOpportunityId());
        if (followup.getOperatorId() == null) {
            followup.setOperatorId(UserContextHolder.currentUserId());
        }
        followupMapper.insert(followup);
        return followup.getId();
    }

    public List<CrmOpportunityFollowup> followupsOf(Long opportunityId) {
        return followupMapper.selectList(new LambdaQueryWrapper<CrmOpportunityFollowup>()
                .eq(CrmOpportunityFollowup::getOpportunityId, opportunityId)
                .orderByDesc(CrmOpportunityFollowup::getId));
    }

    /** FR-CRM-06：转合同（带出客户/联系人/商机名/预计金额；状态 WON；漏斗推进） */
    @Audited(module = "opportunity", action = "convert_opportunity", targetType = "crm_opportunity", targetId = "{0}")
    @Transactional
    public Long convert(Long id, Long contractId) {
        CrmOpportunity opp = mustGet(id);
        if (!"TRACKING".equals(opp.getStatus())) {
            throw BizException.badRequest("仅跟踪中的商机可转合同");
        }
        opp.setStatus("WON");
        opp.setConvertedContractId(contractId);
        opportunityMapper.updateById(opp);
        exitFunnel(id, "OPPORTUNITY");
        enterFunnel(id, "CONTRACT", opp.getExpectAmount());
        return opp.getId();
    }

    /** FR-CRM-07：丢单（必填原因；状态 LOST；不物理删除） */
    @Audited(module = "opportunity", action = "lose_opportunity", targetType = "crm_opportunity", targetId = "{0}")
    @Transactional
    public void lose(Long id, String lostReason) {
        if (lostReason == null || lostReason.length() < 4) {
            throw BizException.badRequest("丢单原因必填（价格过高/竞对优势/预算取消/其他+说明）");
        }
        CrmOpportunity opp = mustGet(id);
        if (!"TRACKING".equals(opp.getStatus())) {
            throw BizException.badRequest("仅跟踪中的商机可标记丢单");
        }
        opp.setStatus("LOST");
        opp.setLostReason(lostReason);
        opportunityMapper.updateById(opp);
        exitFunnel(id, "OPPORTUNITY");
    }

    /** FR-CRM-08：重新激活（R05/R01 由权限层控制） */
    @Audited(module = "opportunity", action = "reopen_opportunity", targetType = "crm_opportunity", targetId = "{0}")
    @Transactional
    public void reopen(Long id) {
        CrmOpportunity opp = mustGet(id);
        if (!"LOST".equals(opp.getStatus())) {
            throw BizException.badRequest("仅已关闭商机可重新激活");
        }
        opp.setStatus("TRACKING");
        opp.setLostReason(null);
        opportunityMapper.updateById(opp);
        enterFunnel(id, "OPPORTUNITY", opp.getExpectAmount());
    }

    /** FR-CRM-13：漏斗统计（四阶段数量与金额分布、平均停留） */
    public Map<String, Object> funnelStats() {
        Map<String, Object> stats = new HashMap<>();
        List<CrmOpportunity> all = opportunityMapper.selectList(new LambdaQueryWrapper<CrmOpportunity>());
        long tracking = all.stream().filter(o -> "TRACKING".equals(o.getStatus())).count();
        long won = all.stream().filter(o -> "WON".equals(o.getStatus())).count();
        long lost = all.stream().filter(o -> "LOST".equals(o.getStatus())).count();
        BigDecimal wonAmount = all.stream().filter(o -> "WON".equals(o.getStatus()) && o.getExpectAmount() != null)
                .map(CrmOpportunity::getExpectAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal trackingAmount = all.stream().filter(o -> "TRACKING".equals(o.getStatus()) && o.getExpectAmount() != null)
                .map(CrmOpportunity::getExpectAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("tracking", tracking);
        stats.put("won", won);
        stats.put("lost", lost);
        stats.put("trackingAmount", trackingAmount);
        stats.put("wonAmount", wonAmount);
        stats.put("winRate", tracking + won == 0 ? 0 : (double) won / (tracking + won));
        return stats;
    }

    private void enterFunnel(Long opportunityId, String stage, BigDecimal amount) {
        CrmSalesFunnel node = new CrmSalesFunnel();
        node.setOpportunityId(opportunityId);
        node.setStage(stage);
        node.setEnterTime(LocalDateTime.now());
        node.setAmount(amount);
        funnelMapper.insert(node);
    }

    private void exitFunnel(Long opportunityId, String stage) {
        CrmSalesFunnel node = funnelMapper.selectOne(new LambdaQueryWrapper<CrmSalesFunnel>()
                .eq(CrmSalesFunnel::getOpportunityId, opportunityId)
                .eq(CrmSalesFunnel::getStage, stage)
                .isNull(CrmSalesFunnel::getExitTime)
                .orderByDesc(CrmSalesFunnel::getId)
                .last("LIMIT 1"));
        if (node != null) {
            node.setExitTime(LocalDateTime.now());
            node.setStayDays((int) ChronoUnit.DAYS.between(node.getEnterTime(), node.getExitTime()));
            funnelMapper.updateById(node);
        }
    }

    private CrmOpportunity mustGet(Long id) {
        CrmOpportunity opp = opportunityMapper.selectById(id);
        if (opp == null) {
            throw BizException.notFound("商机不存在");
        }
        return opp;
    }
}
