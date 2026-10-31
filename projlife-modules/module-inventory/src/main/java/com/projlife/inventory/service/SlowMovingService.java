package com.projlife.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.inventory.entity.InvDisposal;
import com.projlife.inventory.entity.InvSlowMoving;
import com.projlife.inventory.mapper.InvDisposalMapper;
import com.projlife.inventory.mapper.InvSlowMovingMapper;
import com.projlife.inventory.mapper.InvBatchMapper;
import com.projlife.inventory.entity.InvBatch;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.security.auth.RequirePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 呆滞与处置服务（V4-04 M07 T-INV-07/08）：
 * CANDIDATE → CONFIRMED（E-38 责任归因必填）→ DISPOSING（处置单 DP 编号，E-39 方式）→ DISPOSED。
 * 处置审批分级沿用 A-31 阈值（<1 万 L1 / <10 万 L2 / 其余 L3）。
 */
@Service
public class SlowMovingService {

    private final InvSlowMovingMapper slowMapper;
    private final InvDisposalMapper disposalMapper;
    private final InvBatchMapper batchMapper;
    private final CodeSequenceService codeSequenceService;

    public SlowMovingService(InvSlowMovingMapper slowMapper, InvDisposalMapper disposalMapper,
                             InvBatchMapper batchMapper, CodeSequenceService codeSequenceService) {
        this.slowMapper = slowMapper;
        this.disposalMapper = disposalMapper;
        this.batchMapper = batchMapper;
        this.codeSequenceService = codeSequenceService;
    }

    /** 呆滞候选登记（来源：库龄分析跑批/ECN 变更，源单可带 source_ecn_id） */
    @RequirePermission("inv:slow:manage")
    @Transactional
    public InvSlowMoving create(InvSlowMoving slow) {
        if (slow.getMaterialId() == null || slow.getQty() == null
                || slow.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "物料与呆滞数量必填（数量须为正）");
        }
        if (slow.getFirstSeenDate() == null) {
            slow.setFirstSeenDate(LocalDate.now());
        }
        slow.setStatus("CANDIDATE");
        slowMapper.insert(slow);
        return slow;
    }

    /** 确认呆滞：E-38 六类责任归因必填 */
    @RequirePermission("inv:slow:manage")
    @Transactional
    public InvSlowMoving confirm(Long id) {
        InvSlowMoving slow = mustGet(id);
        if (!"CANDIDATE".equals(slow.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "仅 CANDIDATE 可确认");
        }
        if (slow.getResponsibility() == null
                || !slow.getResponsibility().matches("DESIGN|PURCHASE_OVER|PRODUCTION_LEFT|ORDER_CANCEL|QUALITY|HISTORY")) {
            throw BizException.blocked("INV-NO-RESPONSIBILITY",
                    "呆滞确认必须指定 E-38 六类责任归因（DESIGN/PURCHASE_OVER/PRODUCTION_LEFT/ORDER_CANCEL/QUALITY/HISTORY）",
                    "请先明确责任归属再确认呆滞（呆滞损失进成本口径）");
        }
        slow.setStatus("CONFIRMED");
        slowMapper.updateById(slow);
        return slow;
    }

    /** 发起处置：E-39 六类方式；数量不得超呆滞余量；审批级按预期金额 A-31 */
    @RequirePermission("inv:slow:manage")
    @Transactional
    public InvDisposal createDisposal(Long slowId, InvDisposal disposal) {
        InvSlowMoving slow = mustGet(slowId);
        if (!"CONFIRMED".equals(slow.getStatus())) {
            throw BizException.blocked("INV-SLOW-NOT-CONFIRMED",
                    "呆滞台账未确认，不得发起处置", "请先完成呆滞确认（E-38 责任归因）");
        }
        if (disposal.getMethod() == null
                || !disposal.getMethod().matches("REUSE|SERVICE|REMODEL|SELL|SCRAP|CUSTOMER")) {
            throw BizException.of(ErrorCode.PARAM_INVALID,
                    "处置方式必须为 E-39 六类（REUSE/SERVICE/REMODEL/SELL/SCRAP/CUSTOMER）");
        }
        if (disposal.getQty() == null || disposal.getQty().compareTo(BigDecimal.ZERO) <= 0
                || disposal.getQty().compareTo(slow.getQty()) > 0) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "处置数量必须为正且不超呆滞余量 " + slow.getQty());
        }
        disposal.setSlowMovingId(slowId);
        disposal.setDisposalNo(codeSequenceService.next(
                CodeTypes.disposal(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
        BigDecimal abs = (disposal.getExpectAmount() == null ? BigDecimal.ZERO : disposal.getExpectAmount()).abs();
        disposal.setApprovalLevel(abs.compareTo(new BigDecimal(10000)) < 0 ? "L1"
                : abs.compareTo(new BigDecimal(100000)) < 0 ? "L2" : "L3");
        disposalMapper.insert(disposal);
        slow.setStatus("DISPOSING");
        slowMapper.updateById(slow);
        return disposal;
    }

    /** 处置审批（L1/L2/L3 动态权限） */
    @Transactional
    public InvDisposal approve(Long disposalId, String level) {
        com.projlife.common.context.LoginUser user = UserContextHolder.get();
        if (user == null || !user.hasPermission("inv:diff:approve:" + level)) {
            throw BizException.forbidden("缺少处置审批权限 inv:diff:approve:" + level);
        }
        InvDisposal disposal = mustDisposal(disposalId);
        if (!disposal.getApprovalLevel().equals(level)) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "审批级别与处置单不符（应为 " + disposal.getApprovalLevel() + "）");
        }
        if (disposal.getApprovedAt() != null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "处置单已审批");
        }
        disposal.setApprovedBy(UserContextHolder.currentUserId());
        disposal.setApprovedAt(LocalDateTime.now());
        disposalMapper.updateById(disposal);
        return disposal;
    }

    /** 处置完成：回填实际金额，台账 DISPOSED */
    @RequirePermission("inv:slow:manage")
    @Transactional
    public InvDisposal complete(Long disposalId, BigDecimal actualAmount) {
        InvDisposal disposal = mustDisposal(disposalId);
        if (disposal.getApprovedAt() == null) {
            throw BizException.blocked("INV-DISPOSAL-NOT-APPROVED",
                    "处置单未审批，不得完成处置", "请先完成 L" + disposal.getApprovalLevel() + " 审批");
        }
        if (disposal.getDisposedAt() != null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "处置单已完成");
        }
        disposal.setActualAmount(actualAmount == null ? BigDecimal.ZERO : actualAmount);
        disposal.setDisposedAt(LocalDateTime.now());
        disposalMapper.updateById(disposal);
        InvSlowMoving slow = mustGet(disposal.getSlowMovingId());
        slow.setStatus("DISPOSED");
        slowMapper.updateById(slow);
        return disposal;
    }

    /** 台账查询（状态/物料过滤） */
    @RequirePermission("inv:slow:manage")
    public List<InvSlowMoving> list(String status, Long materialId) {
        return slowMapper.selectList(new LambdaQueryWrapper<InvSlowMoving>()
                .eq(status != null, InvSlowMoving::getStatus, status)
                .eq(materialId != null, InvSlowMoving::getMaterialId, materialId)
                .eq(InvSlowMoving::getDeleted, 0).orderByDesc(InvSlowMoving::getId));
    }

    private InvSlowMoving mustGet(Long id) {
        InvSlowMoving slow = slowMapper.selectById(id);
        if (slow == null) {
            throw BizException.notFound("呆滞台账不存在: " + id);
        }
        return slow;
    }

    private InvDisposal mustDisposal(Long id) {
        InvDisposal disposal = disposalMapper.selectById(id);
        if (disposal == null) {
            throw BizException.notFound("处置单不存在: " + id);
        }
        return disposal;
    }

    /** 库龄自动跑批（S12 遗留，R5 落地）：批次库龄 ≥threshold 天且数量>0 → 呆滞 CANDIDATE（同物料未闭环则跳过） */
    @Transactional
    public int autoScanFromBatches(int ageThresholdDays) {
        LocalDate cutoff = LocalDate.now().minusDays(ageThresholdDays);
        List<InvBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<InvBatch>()
                .le(InvBatch::getReceiptDate, cutoff)
                .gt(InvBatch::getQty, 0)
                .eq(InvBatch::getDeleted, 0));
        int created = 0;
        for (InvBatch batch : batches) {
            // 同物料已有未闭环台账（CANDIDATE/CONFIRMED/DISPOSING）则跳过，避免重复登记
            Long open = slowMapper.selectCount(new LambdaQueryWrapper<InvSlowMoving>()
                    .eq(InvSlowMoving::getMaterialId, batch.getMaterialId())
                    .in(InvSlowMoving::getStatus, "CANDIDATE", "CONFIRMED", "DISPOSING")
                    .eq(InvSlowMoving::getDeleted, 0));
            if (open != null && open > 0) {
                continue;
            }
            InvSlowMoving slow = new InvSlowMoving();
            slow.setMaterialId(batch.getMaterialId());
            slow.setLocationId(batch.getLocationId());
            slow.setQty(batch.getQty());
            slow.setAgeDays((int) java.time.temporal.ChronoUnit.DAYS.between(
                    batch.getReceiptDate(), LocalDate.now()));
            slow.setFirstSeenDate(LocalDate.now());
            slow.setStatus("CANDIDATE");
            slowMapper.insert(slow);
            created++;
        }
        return created;
    }
}
