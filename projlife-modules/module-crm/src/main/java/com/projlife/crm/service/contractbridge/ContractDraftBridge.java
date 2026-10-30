package com.projlife.crm.service.contractbridge;

/**
 * 报价转合同桥接接口（FR-CRM-19）：由 module-contract 实现，避免 crm → contract 的模块依赖。
 */
public interface ContractDraftBridge {

    /**
     * 依据中标报价单生成合同草稿（带出客户/金额/产品线/付款条件）。
     *
     * @return 合同草稿 id
     */
    Long createFromQuotation(com.projlife.crm.entity.CrmQuotation quotation);
}
