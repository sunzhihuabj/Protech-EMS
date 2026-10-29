package com.projlife.scm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.mdm.entity.MdSupplier;
import com.projlife.mdm.mapper.MdSupplierMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.scm.entity.ScmPurchaseOrder;
import com.projlife.scm.entity.ScmPurchaseOrderItem;
import com.projlife.scm.entity.ScmPurchaseRequirement;
import com.projlife.scm.entity.ScmSupplierOnboarding;
import com.projlife.scm.mapper.ScmPurchaseOrderItemMapper;
import com.projlife.scm.mapper.ScmPurchaseOrderMapper;
import com.projlife.scm.mapper.ScmPurchaseRequirementMapper;
import com.projlife.scm.mapper.ScmSupplierOnboardingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 供应链（07 册 §2）：供应商五阶准入（BR-SCM-01）、采购需求四来源、采购订单（价格脱敏 BR-SCM-03）。
 */
@Service
public class ScmService {

    /** 五阶顺序 */
    private static final List<String> STAGES = List.of(
            "REGISTERED", "QUALIFIED", "VISITED", "SAMPLED", "APPROVED");

    private final ScmSupplierOnboardingMapper onboardingMapper;
    private final MdSupplierMapper supplierMapper;
    private final ScmPurchaseRequirementMapper requirementMapper;
    private final ScmPurchaseOrderMapper orderMapper;
    private final ScmPurchaseOrderItemMapper orderItemMapper;
    private final CodeSequenceService codeSequenceService;

    public ScmService(ScmSupplierOnboardingMapper onboardingMapper, MdSupplierMapper supplierMapper,
                      ScmPurchaseRequirementMapper requirementMapper, ScmPurchaseOrderMapper orderMapper,
                      ScmPurchaseOrderItemMapper orderItemMapper, CodeSequenceService codeSequenceService) {
        this.onboardingMapper = onboardingMapper;
        this.supplierMapper = supplierMapper;
        this.requirementMapper = requirementMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.codeSequenceService = codeSequenceService;
    }

    // ---------- 供应商准入 ----------

    public ScmSupplierOnboarding onboardingOf(Long supplierId) {
        return onboardingMapper.selectOne(new LambdaQueryWrapper<ScmSupplierOnboarding>()
                .eq(ScmSupplierOnboarding::getSupplierId, supplierId)
                .last("LIMIT 1"));
    }

    /** FR-SCM-01：注册（建准入档案） */
    @Audited(module = "scm", action = "register_supplier", targetType = "scm_supplier_onboarding", targetId = "{0.supplierId}")
    @Transactional
    public Long register(Long supplierId) {
        if (onboardingOf(supplierId) != null) {
            throw BizException.badRequest("该供应商已有准入档案");
        }
        ScmSupplierOnboarding onboarding = new ScmSupplierOnboarding();
        onboarding.setSupplierId(supplierId);
        onboarding.setCurrentStage("REGISTERED");
        onboarding.setStageStatus("PASSED");
        onboarding.setApplyBy(UserContextHolder.currentUserId());
        onboardingMapper.insert(onboarding);
        return onboarding.getId();
    }

    /** FR-SCM-02：五阶流转（上一阶通过才能进下一阶；REJECTED 可重新申请） */
    @Audited(module = "scm", action = "advance_stage", targetType = "scm_supplier_onboarding", targetId = "{0.supplierId}")
    @Transactional
    public ScmSupplierOnboarding advanceStage(Long supplierId, boolean passed, String auditResult) {
        ScmSupplierOnboarding onboarding = onboardingOf(supplierId);
        if (onboarding == null) {
            throw BizException.notFound("准入档案不存在，请先注册");
        }
        if (!passed) {
            onboarding.setStageStatus("FAILED");
            onboarding.setCurrentStage("REJECTED");
            onboarding.setAuditResult(auditResult);
            onboardingMapper.updateById(onboarding);
            return onboarding;
        }
        int idx = STAGES.indexOf(onboarding.getCurrentStage());
        if (idx < 0 || idx >= STAGES.size() - 1) {
            throw BizException.badRequest("当前阶段不可推进（" + onboarding.getCurrentStage() + "）");
        }
        onboarding.setCurrentStage(STAGES.get(idx + 1));
        onboarding.setStageStatus("PASSED");
        onboarding.setAuditResult(auditResult);
        if ("APPROVED".equals(onboarding.getCurrentStage())) {
            onboarding.setFinalLevel("QUALIFIED");
            // 准入完成 → md_supplier 置合格
            MdSupplier supplier = supplierMapper.selectById(supplierId);
            if (supplier != null) {
                supplier.setStatus("QUALIFIED");
                supplierMapper.updateById(supplier);
            }
        }
        onboardingMapper.updateById(onboarding);
        return onboarding;
    }

    /** BR-SCM-01：下单前校验准入 */
    public void assertApproved(Long supplierId) {
        ScmSupplierOnboarding onboarding = onboardingOf(supplierId);
        if (onboarding == null || !"APPROVED".equals(onboarding.getCurrentStage())) {
            throw BizException.of(5001, "供应商未完成准入，不得下单")
                    .with("blockCode", "BR-SCM-01");
        }
    }

    // ---------- 采购需求 ----------

    public PageResp<ScmPurchaseRequirement> requirements(PageReq pageReq, String status) {
        LambdaQueryWrapper<ScmPurchaseRequirement> wrapper = new LambdaQueryWrapper<ScmPurchaseRequirement>()
                .orderByDesc(ScmPurchaseRequirement::getId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ScmPurchaseRequirement::getStatus, status);
        }
        Page<ScmPurchaseRequirement> page = requirementMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<ScmPurchaseRequirement> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** FR-SCM-11/12：采购需求（四来源；①③来源必挂项目） */
    @Audited(module = "scm", action = "create_requirement", targetType = "scm_purchase_requirement", targetId = "{0.reqNo}")
    @Transactional
    public Long createRequirement(ScmPurchaseRequirement requirement) {
        String sourceType = requirement.getSourceType();
        if (!"BOM_SHORTAGE".equals(sourceType) && !"SAFETY_STOCK".equals(sourceType)
                && !"MANUAL".equals(sourceType) && !"STOCK".equals(sourceType)) {
            throw BizException.badRequest("来源必须为四类之一（FR-SCM-11）");
        }
        if (("BOM_SHORTAGE".equals(sourceType) || "MANUAL".equals(sourceType))
                && requirement.getProjectId() == null) {
            throw BizException.badRequest("来源 " + sourceType + " 必挂项目（FR-SCM-12）");
        }
        if (requirement.getQty() == null || requirement.getQty().signum() <= 0) {
            throw BizException.badRequest("数量必须 >0");
        }
        requirement.setReqNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "PR:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")),
                "PR-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-", 4)));
        requirement.setStatus("OPEN");
        requirement.setRequesterId(UserContextHolder.currentUserId());
        requirementMapper.insert(requirement);
        return requirement.getId();
    }

    // ---------- 采购订单 ----------

    public PageResp<ScmPurchaseOrder> orders(PageReq pageReq, String status) {
        LambdaQueryWrapper<ScmPurchaseOrder> wrapper = new LambdaQueryWrapper<ScmPurchaseOrder>()
                .orderByDesc(ScmPurchaseOrder::getId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ScmPurchaseOrder::getStatus, status);
        }
        Page<ScmPurchaseOrder> page = orderMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<ScmPurchaseOrder> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    public ScmPurchaseOrder getById(Long id) {
        return orderMapper.selectById(id);
    }

    public List<ScmPurchaseOrderItem> itemsOf(Long poId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<ScmPurchaseOrderItem>()
                .eq(ScmPurchaseOrderItem::getPoId, poId)
                .orderByAsc(ScmPurchaseOrderItem::getLineNo));
    }

    /** FR-SCM-15：创建 PO（合并需求；供应商必须 APPROVED，BR-SCM-01） */
    @Audited(module = "scm", action = "create_po", targetType = "scm_purchase_order", targetId = "{0.poNo}")
    @Transactional
    public Long createOrder(ScmPurchaseOrder order, List<ScmPurchaseOrderItem> items, List<Long> requirementIds) {
        assertApproved(order.getSupplierId());
        order.setPoNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "PO:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")),
                "PO-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-", 4)));
        order.setStatus("DRAFT");
        order.setBuyerId(UserContextHolder.currentUserId());
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 0;
        orderMapper.insert(order);
        for (ScmPurchaseOrderItem item : items) {
            item.setPoId(order.getId());
            item.setLineNo(++lineNo);
            if (item.getUnitPrice() != null && item.getQty() != null) {
                item.setAmount(item.getUnitPrice().multiply(item.getQty()));
                total = total.add(item.getAmount());
            }
            orderItemMapper.insert(item);
        }
        order.setTotalAmount(total);
        orderMapper.updateById(order);
        // 需求合并标记（FR-SCM-13）
        if (requirementIds != null) {
            for (Long reqId : requirementIds) {
                ScmPurchaseRequirement requirement = requirementMapper.selectById(reqId);
                if (requirement != null) {
                    requirement.setStatus("MERGED");
                    requirement.setMergePoId(order.getId());
                    requirementMapper.updateById(requirement);
                }
            }
        }
        return order.getId();
    }

    /** FR-SCM-17 + BR-SCM-02：下发（此后不可改供应商与单价） */
    @Audited(module = "scm", action = "dispatch_po", targetType = "scm_purchase_order", targetId = "{0}")
    @Transactional
    public void dispatch(Long poId) {
        ScmPurchaseOrder order = orderMapper.selectById(poId);
        if (order == null) {
            throw BizException.notFound("采购订单不存在");
        }
        if (!"DRAFT".equals(order.getStatus())) {
            throw BizException.badRequest("仅草稿可下发（BR-SCM-02 下发后不可改）");
        }
        order.setStatus("DISPATCHED");
        order.setDispatchedAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }
}
