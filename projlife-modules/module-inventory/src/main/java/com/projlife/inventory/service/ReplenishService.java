package com.projlife.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.common.kv.KeyValStore;
import com.projlife.inventory.entity.InvReplenish;
import com.projlife.inventory.entity.InvReplenishLine;
import com.projlife.inventory.mapper.InvReplenishLineMapper;
import com.projlife.inventory.mapper.InvReplenishMapper;

import com.projlife.mdm.entity.MdMaterial;
import com.projlife.mdm.mapper.MdMaterialMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 补货（07 册 §3.5 + BR-05）：分级审批 / 安全件先发后签 4h 出库 24h 补签 / 25h 未补签锁定申请人。
 */
@Service
public class ReplenishService {

    private static final String LOCK_KEY_PREFIX = "replenish:lock:";
    /** 安全件出库 SLA（小时） */
    private static final int ISSUE_SLA_HOURS = 4;
    /** 补签时限（小时） */
    private static final int BACKFILL_HOURS = 24;

    private final InvReplenishMapper replenishMapper;
    private final InvReplenishLineMapper lineMapper;
    private final MdMaterialMapper materialMapper;
    private final CodeSequenceService codeSequenceService;
    private final KeyValStore keyValStore;

    @Value("${projlife.inv.safety-materials:}")
    private String safetyMaterialCodes;

    public ReplenishService(InvReplenishMapper replenishMapper, InvReplenishLineMapper lineMapper,
                            MdMaterialMapper materialMapper, CodeSequenceService codeSequenceService,
                            KeyValStore keyValStore) {
        this.replenishMapper = replenishMapper;
        this.lineMapper = lineMapper;
        this.materialMapper = materialMapper;
        this.codeSequenceService = codeSequenceService;
        this.keyValStore = keyValStore;
    }

    public PageResp<InvReplenish> page(PageReq pageReq, String status, Long applicantId) {
        LambdaQueryWrapper<InvReplenish> wrapper = new LambdaQueryWrapper<InvReplenish>()
                .orderByDesc(InvReplenish::getId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(InvReplenish::getStatus, status);
        }
        if (applicantId != null) {
            wrapper.eq(InvReplenish::getApplicantId, applicantId);
        }
        Page<InvReplenish> page = replenishMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<InvReplenish> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    public List<InvReplenishLine> linesOf(Long replenishId) {
        return lineMapper.selectList(new LambdaQueryWrapper<InvReplenishLine>()
                .eq(InvReplenishLine::getReplenishId, replenishId));
    }

    /** BR-05 前置：被锁定的申请人新申请被拒 */
    private void checkApplicantLock(Long applicantId) {
        if (keyValStore.exists(LOCK_KEY_PREFIX + applicantId)) {
            throw BizException.of(ErrorCode.BLOCKED, "上笔安全件补货超 24h 未补签，新申请已锁定")
                    .with("blockCode", "BR-05")
                    .with("suggestion", "请先完成上笔补签，或联系链主处理");
        }
    }

    /** FR-INV-33：补货申请（BR-03 挂项目；安全件识别） */
    @Audited(module = "inv", action = "create_replenish", targetType = "inv_replenish", targetId = "{0.replenishNo}")
    @Transactional
    public Long create(InvReplenish replenish, List<InvReplenishLine> lines) {
        if (replenish.getMdProjectId() == null) {
            throw BizException.badRequest("补货必须挂项目档案（BR-03 无项目编码不得报销/发料）");
        }
        if (replenish.getReason() == null || replenish.getReason().isEmpty()) {
            throw BizException.badRequest("补货原因必填（E-40 八类）");
        }
        checkApplicantLock(replenish.getApplicantId() == null
                ? UserContextHolder.currentUserId() : replenish.getApplicantId());
        boolean hasSafety = false;
        for (InvReplenishLine line : lines) {
            MdMaterial material = materialMapper.selectById(line.getMaterialId());
            if (material == null) {
                throw BizException.badRequest("物料不存在：" + line.getMaterialId());
            }
            if (isSafetyMaterial(material.getMaterialCode())) {
                hasSafety = true;
            }
        }
        replenish.setReplenishNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "RP:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                "RP-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-", 3)));
        // FR-INV-36：安全件先发后签
        replenish.setNeedBackfill(hasSafety ? 1 : 0);
        replenish.setStatus("PENDING");
        if (replenish.getApplicantId() == null) {
            replenish.setApplicantId(UserContextHolder.currentUserId());
        }
        replenishMapper.insert(replenish);
        for (InvReplenishLine line : lines) {
            line.setReplenishId(replenish.getId());
            lineMapper.insert(line);
        }
        return replenish.getId();
    }

    private boolean isSafetyMaterial(String materialCode) {
        if (safetyMaterialCodes == null || safetyMaterialCodes.isEmpty()) {
            return false;
        }
        for (String code : safetyMaterialCodes.split(",")) {
            if (code.trim().equals(materialCode)) {
                return true;
            }
        }
        return false;
    }

    /** FR-INV-35：审批（金额分级由服务端判定，BR-INV-06） */
    @Audited(module = "inv", action = "approve_replenish", targetType = "inv_replenish", targetId = "{0}")
    @Transactional
    public String approve(Long replenishId) {
        InvReplenish replenish = mustGet(replenishId);
        if (!"PENDING".equals(replenish.getStatus())) {
            throw BizException.badRequest("仅待审批可审批");
        }
        java.math.BigDecimal amount = replenish.getAmount() == null
                ? java.math.BigDecimal.ZERO : replenish.getAmount();
        String path;
        if (amount.compareTo(new java.math.BigDecimal("500")) < 0) {
            path = "L1";
        } else if (amount.compareTo(new java.math.BigDecimal("5000")) < 0) {
            path = "L1-L2";
        } else {
            path = "L1-L2-L3";
        }
        replenish.setApprovalPath(path);
        replenish.setStatus("APPROVED");
        replenishMapper.updateById(replenish);
        return path;
    }

    /** 出库：安全件置补签时限（出库+24h，BR-05） */
    @Transactional
    public void issue(Long replenishId) {
        InvReplenish replenish = mustGet(replenishId);
        if (!"APPROVED".equals(replenish.getStatus())) {
            throw BizException.badRequest("仅已审批可出库");
        }
        replenish.setStatus("ISSUED");
        if (replenish.getNeedBackfill() == 1) {
            replenish.setBackfillDeadline(LocalDateTime.now().plusHours(BACKFILL_HOURS));
        }
        replenishMapper.updateById(replenish);
    }

    /** FR-INV-36：补签 */
    @Audited(module = "inv", action = "backfill_replenish", targetType = "inv_replenish", targetId = "{0}")
    @Transactional
    public void backfill(Long replenishId) {
        InvReplenish replenish = mustGet(replenishId);
        if (!"ISSUED".equals(replenish.getStatus()) || replenish.getNeedBackfill() != 1) {
            throw BizException.badRequest("仅安全件已出库单可补签");
        }
        replenish.setStatus("BACKFILLED");
        replenish.setBackfilledAt(LocalDateTime.now());
        replenishMapper.updateById(replenish);
        keyValStore.remove(LOCK_KEY_PREFIX + replenish.getApplicantId());
    }

    /** BR-05：25h 未补签 → 锁定申请人（job 每小时入口） */
    @Transactional
    public int lockOverdueBackfills() {
        List<InvReplenish> overdue = replenishMapper.selectList(new LambdaQueryWrapper<InvReplenish>()
                .eq(InvReplenish::getStatus, "ISSUED")
                .eq(InvReplenish::getNeedBackfill, 1)
                .lt(InvReplenish::getBackfillDeadline, LocalDateTime.now().minusHours(1))
                .eq(InvReplenish::getApplicantLocked, 0));
        int count = 0;
        for (InvReplenish replenish : overdue) {
            replenish.setStatus("LOCKED");
            replenish.setApplicantLocked(1);
            replenishMapper.updateById(replenish);
            // 锁定 24h（到期自动解除由审批/补签清除；此处键 TTL 与锁定时长一致）
            keyValStore.put(LOCK_KEY_PREFIX + replenish.getApplicantId(), replenish.getReplenishNo(),
                    Duration.ofHours(24).toSeconds());
            count++;
        }
        return count;
    }

    private InvReplenish mustGet(Long id) {
        InvReplenish replenish = replenishMapper.selectById(id);
        if (replenish == null) {
            throw BizException.notFound("补货单不存在");
        }
        return replenish;
    }
}
