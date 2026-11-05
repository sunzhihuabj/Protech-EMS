package com.projlife.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.contract.entity.CtContract;
import com.projlife.contract.entity.CtContractPaymentPlan;
import com.projlife.contract.entity.CtInvoice;
import com.projlife.contract.entity.CtReceipt;
import com.projlife.contract.mapper.CtContractMapper;
import com.projlife.contract.mapper.CtContractPaymentPlanMapper;
import com.projlife.contract.mapper.CtInvoiceMapper;
import com.projlife.contract.mapper.CtReceiptMapper;
import com.projlife.org.service.audit.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 开票与回款（04 册 §3.6 + §3.7）。
 * FR-CT-36/37 收款录入与财务确认；BR-02 超期未建档冻结开票（开票接口强校验 + 惰性冻结）；
 * BR-CT-07 开票累计不得超合同额；BR-CT-08 无发票回款单列预收款。
 */
@Service
public class ContractFinanceService {

    private final CtContractMapper contractMapper;
    private final CtContractPaymentPlanMapper planMapper;
    private final CtInvoiceMapper invoiceMapper;
    private final CtReceiptMapper receiptMapper;
    private final com.projlife.mdm.api.MdmApi mdmApi;
    private final com.projlife.org.service.NotificationService notificationService;
    private final com.projlife.org.service.code.CodeSequenceService codeSequenceService;

    public ContractFinanceService(CtContractMapper contractMapper, CtContractPaymentPlanMapper planMapper,
                                  CtInvoiceMapper invoiceMapper, CtReceiptMapper receiptMapper,
                                  com.projlife.mdm.api.MdmApi mdmApi,
                                  com.projlife.org.service.NotificationService notificationService,
                                  com.projlife.org.service.code.CodeSequenceService codeSequenceService) {
        this.contractMapper = contractMapper;
        this.planMapper = planMapper;
        this.invoiceMapper = invoiceMapper;
        this.receiptMapper = receiptMapper;
        this.mdmApi = mdmApi;
        this.notificationService = notificationService;
        this.codeSequenceService = codeSequenceService;
    }

    /** FR-CT-25 + BR-02：惰性冻结判定（job-worker 每日批量标记，接口层再校验双保险） */
    private CtContract enforceFilingDeadline(CtContract contract) {
        if (contract.getFiledAt() == null && contract.getFilingDeadline() != null
                && LocalDate.now().isAfter(contract.getFilingDeadline())
                && !"CLOSED".equals(contract.getStatus())) {
            if (contract.getInvoiceFrozen() == null || contract.getInvoiceFrozen() == 0) {
                contract.setInvoiceFrozen(1);
                contractMapper.updateById(contract);
            }
            throw BizException.of(ErrorCode.BLOCKED, "用印后 3 个工作日未建档，开票已冻结")
                    .with("blockCode", "BR-02")
                    .with("suggestion", "请先完成建档备案，或申请紧急解冻（R02 审批）");
        }
        if (contract.getInvoiceFrozen() != null && contract.getInvoiceFrozen() == 1) {
            throw BizException.of(ErrorCode.BLOCKED, "该合同开票已冻结（未按期建档）")
                    .with("blockCode", "BR-02")
                    .with("suggestion", "完成建档自动解冻，或走紧急解冻流程");
        }
        return contract;
    }

    /** 开票申请（BR-02 校验 + BR-CT-07 超额校验） */
    @Audited(module = "contract", action = "apply_invoice", targetType = "ct_invoice", targetId = "{0.invoiceNo}")
    @Transactional
    public Long applyInvoice(CtInvoice invoice) {
        CtContract contract = mustGet(invoice.getContractId());
        enforceFilingDeadline(contract);
        if (invoice.getAmount() == null || invoice.getAmount().signum() <= 0) {
            throw BizException.badRequest("开票金额必填且 >0");
        }
        BigDecimal invoiced = invoiceMapper.selectList(new LambdaQueryWrapper<CtInvoice>()
                        .eq(CtInvoice::getContractId, contract.getId())
                        .ne(CtInvoice::getStatus, "REJECTED"))
                .stream().map(CtInvoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (invoiced.add(invoice.getAmount()).compareTo(contract.getAmount()) > 0) {
            throw BizException.badRequest("开票累计不得超过合同金额（BR-CT-07），已开 " + invoiced);
        }
        invoice.setStatus("APPLIED");
        invoice.setApplyBy(UserContextHolder.currentUserId());
        invoice.setTaxAmount(invoice.getAmount().multiply(
                invoice.getTaxRate() == null ? contract.getTaxRate() : invoice.getTaxRate()));
        invoiceMapper.insert(invoice);
        return invoice.getId();
    }

    /** 财务确认开票（contract:invoice:confirm，R15） */
    @Audited(module = "contract", action = "confirm_invoice", targetType = "ct_invoice", targetId = "{0}")
    @Transactional
    public void confirmInvoice(Long invoiceId, String tPlusRef) {
        CtInvoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            throw BizException.notFound("发票不存在");
        }
        invoice.setStatus("CONFIRMED");
        invoice.setConfirmBy(UserContextHolder.currentUserId());
        invoice.setTPlusRef(tPlusRef);
        invoiceMapper.updateById(invoice);
    }

    /** FR-CT-36：收款录入（商务） */
    @Audited(module = "contract", action = "apply_receipt", targetType = "ct_receipt", targetId = "{0.receiptNo}")
    @Transactional
    public Long applyReceipt(CtReceipt receipt) {
        CtContract contract = mustGet(receipt.getContractId());
        if (receipt.getAmount() == null || receipt.getAmount().signum() <= 0) {
            throw BizException.badRequest("回款金额必填且 >0");
        }
        // SPRINT-SEC3：取号收敛走 CodeSequenceService（并发安全，替代 selectCount+1 手工推号）
        String receiptNo = codeSequenceService.next(com.projlife.org.service.code.CodeTypes.receipt(
                LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))));
        receipt.setReceiptNo(receiptNo);
        receipt.setStatus("PENDING");
        receipt.setProjectId(resolveProjectId(contract));
        receiptMapper.insert(receipt);
        return receipt.getId();
    }

    /** FR-CT-37：财务确认——更新计划行状态与累计回款（R15 权限） */
    @Audited(module = "contract", action = "confirm_receipt", targetType = "ct_receipt", targetId = "{0}")
    @Transactional
    public void confirmReceipt(Long receiptId) {
        CtReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw BizException.notFound("回款单不存在");
        }
        if ("CONFIRMED".equals(receipt.getStatus())) {
            throw BizException.badRequest("回款单已确认，禁止重复确认");
        }
        receipt.setStatus("CONFIRMED");
        receipt.setConfirmBy(UserContextHolder.currentUserId());
        receiptMapper.updateById(receipt);
        if (receipt.getPaymentPlanId() != null) {
            CtContractPaymentPlan plan = planMapper.selectById(receipt.getPaymentPlanId());
            if (plan != null) {
                BigDecimal received = plan.getActualAmount() == null ? BigDecimal.ZERO : plan.getActualAmount();
                received = received.add(receipt.getAmount());
                plan.setActualAmount(received);
                plan.setActualDate(receipt.getReceiptDate());
                plan.setStatus(received.compareTo(plan.getAmount()) >= 0 ? "RECEIVED" : "PARTIAL");
                planMapper.updateById(plan);
            }
        }
    }

    /** FR-CT-38：逾期计划行（计划日 < 今日 且未收齐） */
    public List<CtContractPaymentPlan> overduePlans() {
        return planMapper.selectList(new LambdaQueryWrapper<CtContractPaymentPlan>()
                .lt(CtContractPaymentPlan::getPlanDate, LocalDate.now())
                .ne(CtContractPaymentPlan::getStatus, "RECEIVED"));
    }

    /** FR-CT-38 job 动作（SPRINT-UID-R18）：逾期收款计划按合同创建人发提醒，返回提醒计划行数 */
    public int remindOverduePlans() {
        List<CtContractPaymentPlan> overdue = overduePlans();
        int count = 0;
        for (CtContractPaymentPlan plan : overdue) {
            CtContract contract = contractMapper.selectById(plan.getContractId());
            if (contract == null || contract.getCreatedBy() == null) {
                continue;
            }
            // SPRINT-SEC3：created_by 存工号（审计填充器），改走按工号入口（原 Long.valueOf 恒抛异常被吞）
            notificationService.sendToUserCode(contract.getCreatedBy(), "CONTRACT_PAYMENT_OVERDUE",
                    "【逾期提醒】收款计划已逾期", contract.getContractName() + " · " + plan.getPhaseName()
                            + " · 计划日 " + plan.getPlanDate() + " · 金额 " + plan.getAmount(),
                    "ct_contract", String.valueOf(plan.getContractId()), "INNER");
            count++;
        }
        return count;
    }

    /** 合同的收款计划明细（FR-CT-04/34） */
    public List<CtContractPaymentPlan> plansOf(Long contractId) {
        return planMapper.selectList(new LambdaQueryWrapper<CtContractPaymentPlan>()
                .eq(CtContractPaymentPlan::getContractId, contractId)
                .orderByAsc(CtContractPaymentPlan::getId));
    }

    /** FR-CT-26：紧急解冻（R02，须填理由；完成建档自动解冻） */
    @Audited(module = "contract", action = "unfreeze_invoice", targetType = "ct_contract", targetId = "{0}")
    @Transactional
    public void unfreeze(Long contractId, String reason) {
        if (reason == null || reason.length() < 10) {
            throw BizException.badRequest("紧急解冻必须填写不少于 10 字的理由（FR-CT-26）");
        }
        CtContract contract = mustGet(contractId);
        contract.setInvoiceFrozen(0);
        contractMapper.updateById(contract);
    }

    /** 关联合同的首个项目（02 册：回款/开票须带 project_id） */
    private Long resolveProjectId(CtContract contract) {
        List<Long> projectIds = mdmApi.linkedProjectIds(contract.getId());
        return projectIds.isEmpty() ? null : projectIds.get(0);
    }

    private CtContract mustGet(Long id) {
        CtContract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw BizException.notFound("合同不存在");
        }
        return contract;
    }
}
