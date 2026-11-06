package com.projlife.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.common.context.UserContextHolder;
import com.projlife.inventory.entity.InvCount;
import com.projlife.inventory.entity.InvCountLine;
import com.projlife.inventory.entity.InvDiff;
import com.projlife.inventory.mapper.InvCountLineMapper;
import com.projlife.inventory.mapper.InvCountMapper;
import com.projlife.inventory.mapper.InvDiffMapper;
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
 * 盘点服务（V4-04 M07 T-INV-06/07）：盘点单 → 录入 → 复核生成差异单（E-37 无归因不调账）→ 分级审批。
 * 差异分级 A-31：|diff_amount| <1 万 L1 / <10 万 L2 / 其余 L3。
 */
@Service
public class InventoryCountService {

    private final InvCountMapper countMapper;
    private final InvCountLineMapper lineMapper;
    private final InvDiffMapper diffMapper;
    private final CodeSequenceService codeSequenceService;

    public InventoryCountService(InvCountMapper countMapper, InvCountLineMapper lineMapper,
                                 InvDiffMapper diffMapper, CodeSequenceService codeSequenceService) {
        this.countMapper = countMapper;
        this.lineMapper = lineMapper;
        this.diffMapper = diffMapper;
        this.codeSequenceService = codeSequenceService;
    }

    /** 创建盘点单（A/B/C/FULL 四类）；盘点行独立传参（实体无 transient 行集） */
    @RequirePermission("inv:count:manage")
    @Transactional
    public InvCount create(InvCount count, List<InvCountLine> lines) {
        if (count.getCountType() == null
                || !count.getCountType().matches("A|B|C|FULL")) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "盘点类型必须为 A/B/C/FULL");
        }
        if (lines == null || lines.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "盘点范围行必填");
        }
        count.setCountNo(codeSequenceService.next(
                CodeTypes.count(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE))));
        count.setStatus("CREATED");
        count.setCreatorId(UserContextHolder.currentUserId());
        countMapper.insert(count);
        for (InvCountLine line : lines) {
            if (line.getMaterialId() == null) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "盘点行物料必填");
            }
            line.setCountId(count.getId());
            lineMapper.insert(line);
        }
        return count;
    }

    /** 盘点行清单 */
    @RequirePermission("inv:count:manage")
    public List<InvCountLine> lines(Long countId) {
        mustGet(countId);
        return lineMapper.selectList(new LambdaQueryWrapper<InvCountLine>()
                .eq(InvCountLine::getCountId, countId).eq(InvCountLine::getDeleted, 0)
                .orderByAsc(InvCountLine::getId));
    }

    /** 开始盘点 */
    @RequirePermission("inv:count:manage")
    @Transactional
    public InvCount start(Long id) {
        InvCount count = mustGet(id);
        if (!"CREATED".equals(count.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "仅 CREATED 可开始盘点");
        }
        count.setStatus("COUNTING");
        count.setStartedAt(LocalDateTime.now());
        countMapper.updateById(count);
        return count;
    }

    /** 录入实盘：diff = actual - book（BR-06：仓储侧不见价格，diff_amount 0 由财务回填） */
    @RequirePermission("inv:count:manage")
    @Transactional
    public int entry(Long countId, List<InvCountLine> actuals) {
        InvCount count = mustGet(countId);
        if (!"COUNTING".equals(count.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "仅 COUNTING 可录入实盘");
        }
        int n = 0;
        for (InvCountLine actual : actuals) {
            InvCountLine line = mustLine(countId, actual.getId());
            if (actual.getActualQty() == null) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "实盘数量必填：行 " + actual.getId());
            }
            line.setActualQty(actual.getActualQty());
            line.setDiffQty(actual.getActualQty()
                    .subtract(line.getBookQty() == null ? BigDecimal.ZERO : line.getBookQty()));
            line.setCause(actual.getCause());
            line.setRemark(actual.getRemark());
            lineMapper.updateById(line);
            n++;
        }
        return n;
    }

    /** 复核：差异行生成差异单；无归因不调账（E-37，blockCode INV-NO-CAUSE） */
    @RequirePermission("inv:count:manage")
    @Transactional
    public List<InvDiff> review(Long countId) {
        InvCount count = mustGet(countId);
        if (!"COUNTING".equals(count.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "仅 COUNTING 可复核");
        }
        List<InvCountLine> lines = lineMapper.selectList(new LambdaQueryWrapper<InvCountLine>()
                .eq(InvCountLine::getCountId, countId).eq(InvCountLine::getDeleted, 0));
        List<InvDiff> created = new java.util.ArrayList<>();
        for (InvCountLine line : lines) {
            if (line.getDiffQty() == null || line.getDiffQty().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            if (line.getCause() == null || line.getCause().isEmpty()) {
                throw BizException.blocked("INV-NO-CAUSE",
                        "差异行缺少归因，无归因不调账（E-37）：行 " + line.getId(), "请补充六类归因后重新复核");
            }
            if (!line.getCause().matches("DOC_LAG|REPLENISH_UNBOOKED|MIX_ENTITY|PICK_ERROR|LOST|MEASURE")) {
                throw BizException.of(ErrorCode.PARAM_INVALID, "归因必须为 E-37 六类（DOC_LAG/REPLENISH_UNBOOKED/MIX_ENTITY/PICK_ERROR/LOST/MEASURE）: " + line.getCause());
            }
            BigDecimal amount = line.getDiffAmount() == null ? BigDecimal.ZERO : line.getDiffAmount();
            InvDiff diff = new InvDiff();
            diff.setDiffNo(codeSequenceService.next(
                    CodeTypes.diff(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
            diff.setCountId(countId);
            diff.setMaterialId(line.getMaterialId());
            diff.setLocationId(line.getLocationId());
            diff.setBookQty(line.getBookQty());
            diff.setActualQty(line.getActualQty());
            diff.setDiffQty(line.getDiffQty());
            diff.setDiffAmount(amount);
            diff.setCause(line.getCause());
            // A-31 分级：<1 万 L1 / <10 万 L2 / 其余 L3
            BigDecimal abs = amount.abs();
            diff.setApprovalLevel(abs.compareTo(new BigDecimal(10000)) < 0 ? "L1"
                    : abs.compareTo(new BigDecimal(100000)) < 0 ? "L2" : "L3");
            diffMapper.insert(diff);
            created.add(diff);
        }
        count.setStatus("DIFF_PROCESS");
        count.setFinishedAt(LocalDateTime.now());
        countMapper.updateById(count);
        return created;
    }

    /** 差异单台账（SPRINT-ORPHAN1 转正：复核生成后此前无查询/审批入口） */
    @RequirePermission("inv:count:manage")
    public List<InvDiff> diffList(Boolean approved) {
        LambdaQueryWrapper<InvDiff> wrapper = new LambdaQueryWrapper<InvDiff>()
                .orderByDesc(InvDiff::getId);
        if (approved != null) {
            if (approved) {
                wrapper.isNotNull(InvDiff::getApprovedAt);
            } else {
                wrapper.isNull(InvDiff::getApprovedAt);
            }
        }
        return diffMapper.selectList(wrapper);
    }

    /** 差异审批：L1/L2/L3 权限分离（动态校验）；通过即闭环 */
    @Transactional
    public InvDiff approve(Long diffId, String level) {
        com.projlife.common.context.LoginUser user = UserContextHolder.get();
        if (user == null || !user.hasPermission("inv:diff:approve:" + level)) {
            throw BizException.forbidden("缺少差异审批权限 inv:diff:approve:" + level);
        }
        InvDiff diff = diffMapper.selectById(diffId);
        if (diff == null) {
            throw BizException.notFound("差异单不存在: " + diffId);
        }
        if (!diff.getApprovalLevel().equals(level)) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "审批级别与差异单不符（应为 " + diff.getApprovalLevel() + "）");
        }
        if (diff.getApprovedAt() != null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "差异单已审批");
        }
        diff.setApprovedBy(UserContextHolder.currentUserId());
        diff.setApprovedAt(LocalDateTime.now());
        diff.setClosedAt(LocalDateTime.now());
        diff.setCloseDays(0);
        diffMapper.updateById(diff);
        return diff;
    }

    private InvCount mustGet(Long id) {
        InvCount count = countMapper.selectById(id);
        if (count == null) {
            throw BizException.notFound("盘点单不存在: " + id);
        }
        return count;
    }

    private InvCountLine mustLine(Long countId, Long lineId) {
        InvCountLine line = lineMapper.selectById(lineId);
        if (line == null || !countId.equals(line.getCountId())) {
            throw BizException.notFound("盘点行不存在或不属于该盘点单: " + lineId);
        }
        return line;
    }
}
