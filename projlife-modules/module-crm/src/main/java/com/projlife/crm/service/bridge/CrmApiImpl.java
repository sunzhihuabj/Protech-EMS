package com.projlife.crm.service.bridge;

import com.projlife.crm.api.CrmApi;
import com.projlife.crm.entity.CrmOpportunity;
import com.projlife.crm.service.OpportunityService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * CrmApi 实现：供 module-bid 等外部模块调用（跨模块只经 api 包）。
 */
@Service
public class CrmApiImpl implements CrmApi {

    private final OpportunityService opportunityService;

    public CrmApiImpl(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    @Override
    public Long createOpportunityFromBid(String oppName, Long customerId, BigDecimal expectAmount,
                                         String summary, Long bidNoticeId) {
        CrmOpportunity opp = new CrmOpportunity();
        opp.setOppName(oppName);
        opp.setCustomerId(customerId);
        opp.setExpectAmount(expectAmount);
        opp.setSummary(summary);
        // FR-BID-12：来源写回 source_type=BID + source_id=公告 id
        opp.setSourceType("BID");
        opp.setSourceId(bidNoticeId);
        if (com.projlife.common.context.UserContextHolder.get() == null) {
            opp.setOwnerId(1L); // Agent 通道无登录上下文，落系统账号（A-28）
        }
        return opportunityService.create(opp);
    }
}
