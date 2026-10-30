package com.projlife.contract.service;

import com.projlife.crm.entity.CrmQuotation;
import com.projlife.crm.service.contractbridge.ContractDraftBridge;
import com.projlife.contract.entity.CtContract;
import com.projlife.contract.mapper.CtContractMapper;
import com.projlife.org.service.audit.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报价转合同桥接实现（FR-CRM-19）：生成合同草稿（客户/金额/产品线带出），
 * 起草后仍走标准评审→用印→建档链路（BR-01 照常生效）。
 */
@Service
public class QuotationContractBridge implements ContractDraftBridge {

    private final CtContractMapper contractMapper;

    public QuotationContractBridge(CtContractMapper contractMapper) {
        this.contractMapper = contractMapper;
    }

    @Override
    @Audited(module = "contract", action = "create_from_quotation", targetType = "ct_contract", targetId = "{0.quotationNo}")
    @Transactional
    public Long createFromQuotation(CrmQuotation quotation) {
        CtContract draft = new CtContract();
        draft.setContractName("报价转合同：" + quotation.getQuotationNo());
        draft.setNature("XS");
        draft.setPartyA("（按报价单客户：" + quotation.getCustomerId() + "，建档后带出）");
        draft.setPartyB("（待补充，从客户档案选择）");
        draft.setCounterpartyId(quotation.getCustomerId());
        draft.setLegalEntityId(1L);
        draft.setBusinessType("SB");
        draft.setAmount(quotation.getAmount());
        draft.setTaxRate(quotation.getTaxRate());
        draft.setPaymentTerms("按报价单交付条件：" + (quotation.getDeliveryTerms() == null ? "见报价单" : quotation.getDeliveryTerms()));
        draft.setSignDate(java.time.LocalDate.now());
        draft.setEffectiveDate(java.time.LocalDate.now());
        draft.setStatus("DRAFT");
        draft.setHandlerId(com.projlife.common.context.UserContextHolder.currentUserId() == null
                ? 0L : com.projlife.common.context.UserContextHolder.currentUserId());
        draft.setQuotationId(quotation.getId());
        contractMapper.insert(draft);
        return draft.getId();
    }
}
