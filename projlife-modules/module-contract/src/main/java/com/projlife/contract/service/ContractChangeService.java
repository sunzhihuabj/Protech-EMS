package com.projlife.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.contract.entity.CtContract;
import com.projlife.contract.entity.CtContractChange;
import com.projlife.contract.mapper.CtContractChangeMapper;
import com.projlife.contract.mapper.CtContractMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 合同变更（04 册 FR-CT-31~33 + BR-CT-01/05）：补充协议 -BCn，旧版本留存，变更生效回原状态。
 */
@Service
public class ContractChangeService {

    private final CtContractChangeMapper changeMapper;
    private final CtContractMapper contractMapper;
    private final CodeSequenceService codeSequenceService;

    public ContractChangeService(CtContractChangeMapper changeMapper, CtContractMapper contractMapper,
                                 CodeSequenceService codeSequenceService) {
        this.changeMapper = changeMapper;
        this.codeSequenceService = codeSequenceService;
        this.contractMapper = contractMapper;
    }

    public List<CtContractChange> changesOf(Long contractId) {
        return changeMapper.selectList(new LambdaQueryWrapper<CtContractChange>()
                .eq(CtContractChange::getContractId, contractId)
                .orderByDesc(CtContractChange::getId));
    }

    /** FR-CT-31：发起变更——生成 -BCn（单合同内序号，BR-CT-01 号不可改、作废不删） */
    @Audited(module = "contract", action = "create_change", targetType = "ct_contract_change", targetId = "{0.changeNo}")
    @Transactional
    public Long create(CtContractChange change) {
        CtContract contract = contractMapper.selectById(change.getContractId());
        if (contract == null) {
            throw BizException.notFound("合同不存在");
        }
        if (contract.getContractNo() == null || contract.getContractNo().isEmpty()) {
            throw BizException.badRequest("未用印合同无合同号，不能发起变更（先完成评审用印）");
        }
        if ("CLOSED".equals(contract.getStatus())) {
            throw BizException.badRequest("已关闭合同不可变更，如需重启请新建合同并关联原合同（BR-CT-05）");
        }
        change.setChangeNo(codeSequenceService.next(CodeTypes.contractChange(contract.getContractNo())));
        change.setApprovalStatus("PENDING");
        change.setBeforeJson(beforeSnapshot(contract));
        changeMapper.insert(change);
        return change.getId();
    }

    /** FR-CT-32/33：审批——通过后生成新版本语义（after 快照落合同变更记录），状态 CHANGED→回原状态 */
    @Audited(module = "contract", action = "approve_change", targetType = "ct_contract_change", targetId = "{0}")
    @Transactional
    public void approve(Long changeId, boolean approved, Long approverId) {
        CtContractChange change = changeMapper.selectById(changeId);
        if (change == null) {
            throw BizException.notFound("变更单不存在");
        }
        if (!"PENDING".equals(change.getApprovalStatus())) {
            throw BizException.badRequest("变更单已审批，禁止重复审批");
        }
        change.setApprovalStatus(approved ? "APPROVED" : "REJECTED");
        change.setApprovedBy(approverId);
        if (approved) {
            CtContract contract = contractMapper.selectById(change.getContractId());
            String originalStatus = contract.getStatus();
            contract.setStatus("CHANGED");
            contractMapper.updateById(contract);
            if (change.getAmountDelta() != null) {
                contract.setAmount(contract.getAmount().add(change.getAmountDelta()));
            }
            contract.setStatus(originalStatus == null ? "PERFORMING" : originalStatus);
            contractMapper.updateById(contract);
            change.setAfterJson(afterSnapshot(change));
        }
        changeMapper.updateById(change);
    }

    private String beforeSnapshot(CtContract contract) {
        return "{\"amount\":" + contract.getAmount() + ",\"status\":\"" + contract.getStatus() + "\"}";
    }

    private String afterSnapshot(CtContractChange change) {
        return "{\"amountDelta\":" + change.getAmountDelta() + ",\"effectiveDate\":\""
                + change.getEffectiveDate() + "\"}";
    }
}
