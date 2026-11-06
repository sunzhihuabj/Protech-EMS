package com.projlife.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.contract.entity.CtContract;
import com.projlife.contract.entity.CtContractPaymentPlan;
import com.projlife.contract.mapper.CtContractMapper;
import com.projlife.contract.mapper.CtContractPaymentPlanMapper;
import com.projlife.mdm.api.MdmApi;
import com.projlife.mdm.entity.MdLegalEntity;
import com.projlife.mdm.mapper.MdLegalEntityMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.common.util.BusinessLogHelper;
import com.projlife.org.service.code.CodeTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 合同全生命周期（04 册 §3：FR-CT-01~04、12~20、21~23）。
 * 状态机按 02 册 §7.1：DRAFT→REVIEWED(BR-01)→SIGNED(用印)→PERFORMING(建档)。
 * 卡点：BR-01 评审必须带项目编码；BR-02 超期未建档冻结开票。
 */
@Service
public class ContractService {

    /** 26 要素（FR-CT-21/22）：A 基本信息 7 / B 商务 6 / C 履约 6 / D 管理 7 */
    private static final String[] FILING_ELEMENTS = {
            "contractName", "counterpartyId", "amount", "nature", "businessType", "taxRate", "legalEntityId",
            "paymentTerms", "signDate", "effectiveDate", "warrantyMonths", "copyCount", "currency",
            "deliveryAddr", "contact", "phone", "acceptanceClause", "deliveryDate", "performStart",
            "performEnd", "handlerId", "reviewerId", "originalFileId", "partyA", "partyB", "legacyNo"
    };

    private final CtContractMapper contractMapper;
    private final CtContractPaymentPlanMapper planMapper;
    private final MdLegalEntityMapper legalEntityMapper;
    private final CodeSequenceService codeSequenceService;
    private final MdmApi mdmApi;
    private final com.projlife.project.api.PrjApi prjApi;
    private final com.projlife.org.service.ApprovalService approvalService;
    private final com.projlife.security.auth.DataScopeGuard dataScopeGuard;
    @org.springframework.beans.factory.annotation.Value("${projlife.approval.enabled:false}")
    private boolean approvalEnabled;

    public ContractService(CtContractMapper contractMapper, CtContractPaymentPlanMapper planMapper,
                           MdLegalEntityMapper legalEntityMapper, CodeSequenceService codeSequenceService,
                           MdmApi mdmApi, com.projlife.project.api.PrjApi prjApi,
                           com.projlife.org.service.ApprovalService approvalService,
                           com.projlife.security.auth.DataScopeGuard dataScopeGuard) {
        this.contractMapper = contractMapper;
        this.planMapper = planMapper;
        this.legalEntityMapper = legalEntityMapper;
        this.codeSequenceService = codeSequenceService;
        this.mdmApi = mdmApi;
        this.prjApi = prjApi;
        this.approvalService = approvalService;
        this.dataScopeGuard = dataScopeGuard;
    }

    public PageResp<CtContract> page(PageReq pageReq, String keyword, String status) {
        LambdaQueryWrapper<CtContract> wrapper = new LambdaQueryWrapper<CtContract>()
                .orderByDesc(CtContract::getId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(CtContract::getContractName, keyword)
                    .or().like(CtContract::getContractNo, keyword));
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(CtContract::getStatus, status);
        }
        Page<CtContract> page = contractMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<CtContract> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** 按 ID 直查数据范围校验（SPRINT-SEC3：SELF/DEPT 档只能看自己/本部门的单据） */
    public CtContract detail(Long id) {
        CtContract contract = mustGet(id);
        dataScopeGuard.checkByOwner(contract.getCreatedBy());
        return contract;
    }

    /** 合同可见性断言（SPRINT-C）：子资源端点（risks/changes/dispatches/related/payment-plans）
     *  此前按合同 ID 直查子表绕过 detail 的守卫，统一先过本断言 */
    public void assertVisible(Long id) {
        dataScopeGuard.checkByOwner(mustGet(id).getCreatedBy());
    }

    /** 关联项目编码（评审提交前至少 1 个，BR-01） */
    public List<Long> linkedProjectIds(Long contractId) {
        return mdmApi.linkedProjectIds(contractId);
    }

    /** FR-CT-01/02/03/04：起草（性质联动校验 + 付款计划多条 + 关联项目） */
    @Audited(module = "contract", action = "create_contract", targetType = "ct_contract", targetId = "{0.contractName}")
    @Transactional
    public Long create(CtContract contract, List<CtContractPaymentPlan> plans, List<Long> projectIds) {
        validateDraft(contract);
        if (contract.getStatus() == null) {
            contract.setStatus("DRAFT");
        }
        contract.setHandlerId(UserContextHolder.currentUserId());
        contract.setInvoiceFrozen(0);
        contract.setAiExtracted(0);
        contractMapper.insert(contract);
        if (plans != null) {
            savePlans(contract.getId(), contract.getAmount(), plans);
        }
        linkProjects(contract.getId(), projectIds);
        return contract.getId();
    }

    private void validateDraft(CtContract contract) {
        if (contract.getContractName() == null || contract.getAmount() == null
                || contract.getSignDate() == null || contract.getEffectiveDate() == null
                || contract.getPaymentTerms() == null || contract.getTaxRate() == null) {
            throw BizException.badRequest("名称/金额/税率/付款条件/签订日/生效日为必填（FR-CT-01）");
        }
        if (contract.getAmount().signum() < 0) {
            throw BizException.badRequest("合同金额须 ≥0");
        }
        // MySQL strict（TEST-REPORT F3）：party_a/party_b/business_type 为 NOT NULL 无默认列
        if (contract.getPartyA() == null || contract.getPartyA().isEmpty()
                || contract.getPartyB() == null || contract.getPartyB().isEmpty()) {
            throw BizException.badRequest("甲乙方主体必填（partyA/partyB）");
        }
        if (contract.getBusinessType() == null || contract.getBusinessType().isEmpty()) {
            throw BizException.badRequest("业务类型必填（businessType：NEW/RENEW/...）");
        }
        if (!"XS".equals(contract.getNature()) && !"CG".equals(contract.getNature())
                && !"KJ".equals(contract.getNature())) {
            throw BizException.badRequest("合同性质必须为 XS/CG/KJ（FR-CT-02）");
        }
        if (contract.getLegalEntityId() == null) {
            throw BizException.badRequest("我方主体必填");
        }
    }

    @Transactional
    public void linkProjects(Long contractId, List<Long> projectIds) {
        if (projectIds == null) {
            return;
        }
        for (Long projectId : projectIds) {
            if (!mdmApi.projectActive(projectId)) {
                throw BizException.badRequest("项目档案不存在或已关闭：" + projectId + "（FR-CT-03 下拉选择，禁止手输）");
            }
            mdmApi.linkContract(projectId, contractId, "OTHER");
        }
    }

    /** 评审提交：BR-01 硬卡点——必须已关联至少 1 个项目编码（422 + blockCode） */
    @Audited(module = "contract", action = "submit_review", targetType = "ct_contract", targetId = "{0}")
    @Transactional
    public void submitReview(Long id) {
        CtContract contract = mustGet(id);
        if (!"DRAFT".equals(contract.getStatus())) {
            throw BizException.badRequest("仅编辑中的合同可提交评审（02 册 §7.1）");
        }
        List<Long> projectIds = linkedProjectIds(id);
        if (projectIds.isEmpty()) {
            throw BizException.of(ErrorCode.BLOCKED, "合同评审必须关联项目编码")
                    .with("blockCode", "BR-01")
                    .with("suggestion", "请先建档取码")
                    .with("link", "/mdm/project/new");
        }
        BusinessLogHelper.transition("合同", contract.getContractName(), "DRAFT", "REVIEWING", "R8 审批流");
        if (approvalEnabled) {
            // R8 审批流：两级（工程主管 → 总经理），末节点回调置 REVIEWED
            List<long[]> chain = List.of(new long[]{0, 4}, new long[]{0, 1});
            approvalService.submit("CONTRACT_REVIEW", id, contract.getContractName(), chain);
            contract.setReviewerId(UserContextHolder.currentUserId());
            contractMapper.updateById(contract);
        } else {
            // 旧流程直达（测试兼容开关，生产置 projlife.approval.enabled=true）
            contract.setStatus("REVIEWED");
            contract.setReviewerId(UserContextHolder.currentUserId());
            contractMapper.updateById(contract);
        }
    }

    /** 审批流回调：监听 ApprovalCompletedEvent（Spring 事件解耦） */
    @org.springframework.context.event.EventListener
    @Transactional
    public void onApprovalComplete(com.projlife.org.service.ApprovalCompletedEvent event) {
        if (!"CONTRACT_REVIEW".equals(event.getApprovalType())) return;
        CtContract contract = mustGet(event.getTargetId());
        contract.setStatus(event.isApproved() ? "REVIEWED" : "DRAFT");
        if (!event.isApproved()) {
            contract.setReviewerId(null);
        }
        contractMapper.updateById(contract);
    }

    /** FR-CT-17/18：编号预览（不占号） */
    public String previewNo(CtContract contract) {
        MdLegalEntity entity = legalEntityMapper.selectById(contract.getLegalEntityId());
        String entityCode = entity == null ? "TLD" : entity.getEntityCode();
        CodeTypes.CodeRequest request = CodeTypes.contract(entityCode,
                String.valueOf(contract.getSignDate().getYear()), contract.getNature(), contract.getBusinessType());
        return request.getDisplayPrefix() + String.format("%04d", nextPreview(request));
    }

    private Long nextPreview(CodeTypes.CodeRequest request) {
        // 预览只读当前值（不加 1 也可能未初始化），展示"下一个将是"即可，占号只在用印时发生
        return codeSequenceService.peek(request) + 1;
    }

    /** FR-CT-17/19/20：用印登记——生成合同号（唯一取号点）、正本、建档时限 */
    @Audited(module = "contract", action = "seal_contract", targetType = "ct_contract", targetId = "{0}")
    @Transactional
    public CtContract seal(Long id, Integer copyCount) {
        CtContract contract = mustGet(id);
        if (!"REVIEWED".equals(contract.getStatus())) {
            throw BizException.badRequest("仅已评审合同可用印（02 册 §7.1）");
        }
        if (copyCount == null || copyCount < 1) {
            throw BizException.badRequest("正本份数 ≥1（FR-CT-19）");
        }
        MdLegalEntity entity = legalEntityMapper.selectById(contract.getLegalEntityId());
        String entityCode = entity == null ? "TLD" : entity.getEntityCode();
        contract.setContractNo(codeSequenceService.next(CodeTypes.contract(entityCode,
                String.valueOf(contract.getSignDate().getYear()), contract.getNature(), contract.getBusinessType())));
        contract.setCopyCount(copyCount);
        contract.setStatus("SIGNED");
        contract.setFilingDeadline(addBusinessDays(contract.getSignDate(), 3));
        contractMapper.updateById(contract);
        return contract;
    }

    /** 工作日顺延（跳过周六日；法定节假日表未建，记 A-12） */
    public static LocalDate addBusinessDays(LocalDate start, int days) {
        LocalDate date = start;
        int added = 0;
        while (added < days) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return date;
    }

    /** FR-CT-21/22/23：建档备案——26 要素完整度必须 100%，完成后 filed_at + PERFORMING + 自动解冻 */
    @Audited(module = "contract", action = "filing_contract", targetType = "ct_contract", targetId = "{0}")
    @Transactional
    public Map<String, Object> filing(Long id, Map<String, Object> elements) {
        CtContract contract = mustGet(id);
        if (!"SIGNED".equals(contract.getStatus())) {
            throw BizException.badRequest("仅已签署合同可建档（02 册 §7.1）");
        }
        applyElements(contract, elements);
        List<String> missing = missingElements(contract);
        if (!missing.isEmpty()) {
            throw BizException.badRequest("档案完整度不足（FR-CT-22），缺失 " + missing.size() + " 项：" + String.join("、", missing));
        }
        contract.setFiledAt(java.time.LocalDateTime.now());
        contract.setStatus("PERFORMING");
        contract.setInvoiceFrozen(0);
        contractMapper.updateById(contract);

        // FR-PRJ-01 自动立项：合同建档即生成执行项目（120 天默认工期，A-15）
        Long prjId = null;
        try {
            List<Long> linkedProjects = mdmApi.linkedProjectIds(contract.getId());
            if (!linkedProjects.isEmpty()) {
                prjId = prjApi.autoCreateFromContract(linkedProjects.get(0), contract.getId(),
                        contract.getContractName(), contract.getSignDate(), 120);
            }
        } catch (Exception ex) {
            // 立项失败不阻断建档（可由 PMO 手工立项补齐），记日志追踪
            org.slf4j.LoggerFactory.getLogger(ContractService.class)
                    .warn("自动立项失败 contractId={}", contract.getId(), ex);
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("filedAt", contract.getFiledAt());
        result.put("completeness", "100%");
        result.put("autoProjectId", prjId);
        return result;
    }

    /** 完整度计算（FR-CT-22）：已填必填 ÷ 26 */
    public Map<String, Object> completeness(CtContract contract) {
        List<String> missing = missingElements(contract);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("total", FILING_ELEMENTS.length);
        result.put("filled", FILING_ELEMENTS.length - missing.size());
        result.put("missing", missing);
        result.put("ratio", (double) (FILING_ELEMENTS.length - missing.size()) / FILING_ELEMENTS.length);
        return result;
    }

    private List<String> missingElements(CtContract c) {
        List<String> missing = new ArrayList<>();
        for (String key : FILING_ELEMENTS) {
            if (valueOf(c, key) == null || (valueOf(c, key) instanceof String && ((String) valueOf(c, key)).isEmpty())) {
                missing.add(key);
            }
        }
        return missing;
    }

    private Object valueOf(CtContract c, String field) {
        try {
            java.lang.reflect.Method getter = CtContract.class.getMethod(
                    "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
            return getter.invoke(c);
        } catch (Exception ex) {
            return null;
        }
    }

    private void applyElements(CtContract contract, Map<String, Object> elements) {
        // 仅允许 26 要素白名单字段回填（防止越权改状态等字段）
        for (String key : FILING_ELEMENTS) {
            Object value = elements.get(key);
            if (value == null) {
                continue;
            }
            try {
                java.lang.reflect.Method setter = CtContract.class.getMethod(
                        "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1), setterType(key));
                setter.invoke(contract, coerce(value, setter.getParameterTypes()[0]));
            } catch (NoSuchMethodException ignored) {
                // 非实体直接承载的要素（如项目关联）由其他途径补齐
            } catch (Exception ex) {
                throw BizException.badRequest("要素 " + key + " 类型不合法");
            }
        }
    }

    private Class<?> setterType(String field) {
        for (java.lang.reflect.Method m : CtContract.class.getMethods()) {
            if (m.getName().equals("set" + Character.toUpperCase(field.charAt(0)) + field.substring(1))
                    && m.getParameterTypes().length == 1) {
                return m.getParameterTypes()[0];
            }
        }
        return Object.class;
    }

    private Object coerce(Object value, Class<?> type) {
        if (type == String.class) {
            return String.valueOf(value);
        }
        if (type == Long.class) {
            return Long.valueOf(String.valueOf(value));
        }
        if (type == Integer.class) {
            return Integer.valueOf(String.valueOf(value));
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(String.valueOf(value));
        }
        if (type == LocalDate.class) {
            return LocalDate.parse(String.valueOf(value).substring(0, 10));
        }
        return value;
    }

    /** 付款计划（FR-CT-04/34）：金额 = 合同额 × 比例；合计须等于合同额（BR-CT-06 口径） */
    private void savePlans(Long contractId, BigDecimal amount, List<CtContractPaymentPlan> plans) {
        BigDecimal total = BigDecimal.ZERO;
        for (CtContractPaymentPlan plan : plans) {
            plan.setContractId(contractId);
            if (plan.getAmount() == null && plan.getPercentage() != null) {
                plan.setAmount(amount.multiply(plan.getPercentage()));
            }
            if (plan.getAmount() == null) {
                throw BizException.badRequest("收款计划行缺少金额/比例");
            }
            total = total.add(plan.getAmount());
            if (plan.getStatus() == null) {
                plan.setStatus("PENDING");
            }
            planMapper.insert(plan);
        }
        if (total.compareTo(amount) != 0) {
            throw BizException.badRequest("付款计划合计须等于合同金额（差异 " + total.subtract(amount) + " 元，BR-CT-06 口径）");
        }
    }

    private CtContract mustGet(Long id) {
        CtContract contract = contractMapper.selectById(id);
        if (contract == null) {
            throw BizException.notFound("合同不存在");
        }
        return contract;
    }
}
