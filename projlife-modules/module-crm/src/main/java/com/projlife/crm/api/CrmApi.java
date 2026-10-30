package com.projlife.crm.api;

import java.math.BigDecimal;

/**
 * 商机跨模块 API（模块间只经 api 包调用，禁止注入对方 Mapper——16 册 §三）。
 */
public interface CrmApi {

    /**
     * FR-BID-12 一键转商机：预填标讯信息，source_type=BID 写回，返回商机 id。
     *
     * @param oppName      商机名（预填：项目名/公告标题）
     * @param customerId   客户主体 id（业主归并后）
     * @param expectAmount 预计金额（公告预算）
     * @param summary      摘要（公告要点）
     * @param bidNoticeId  标讯公告 id（source_id 写回）
     * @return 商机 id
     */
    Long createOpportunityFromBid(String oppName, Long customerId, BigDecimal expectAmount,
                                  String summary, Long bidNoticeId);
}
