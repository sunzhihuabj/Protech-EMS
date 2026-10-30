package com.projlife.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.exception.BizException;
import com.projlife.cost.entity.CostBudget;
import com.projlife.cost.entity.CostEvm;
import com.projlife.cost.entity.CostItem;
import com.projlife.cost.entity.CostSnapshot;
import com.projlife.cost.mapper.CostBudgetMapper;
import com.projlife.cost.mapper.CostEvmMapper;
import com.projlife.cost.mapper.CostItemMapper;
import com.projlife.cost.mapper.CostSnapshotMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本（V4-05 M12 §2.1：FR-COST-01~12）：七项归集/合计校验/超支预警/EVM/每日快照。
 */
@Service
public class CostService {

    private static final String[] SEVEN_TYPES = {
            "MATERIAL", "LABOR", "OUTSOURCE", "EXPENSE", "SITE", "QUALITY", "MGMT"};

    private final CostItemMapper itemMapper;
    private final CostBudgetMapper budgetMapper;
    private final CostSnapshotMapper snapshotMapper;
    private final CostEvmMapper evmMapper;
    private final CodeSequenceService codeSequenceService;

    public CostService(CostItemMapper itemMapper, CostBudgetMapper budgetMapper,
                       CostSnapshotMapper snapshotMapper, CostEvmMapper evmMapper,
                       CodeSequenceService codeSequenceService) {
        this.itemMapper = itemMapper;
        this.budgetMapper = budgetMapper;
        this.snapshotMapper = snapshotMapper;
        this.evmMapper = evmMapper;
        this.codeSequenceService = codeSequenceService;
    }

    public PageResp<CostItem> items(PageReq pageReq, Long projectId) {
        LambdaQueryWrapper<CostItem> wrapper = new LambdaQueryWrapper<CostItem>()
                .orderByDesc(CostItem::getOccurredDate);
        if (projectId != null) {
            wrapper.eq(CostItem::getProjectId, projectId);
        }
        Page<CostItem> page = itemMapper.selectPage(
                new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<CostItem> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** FR-COST-01：成本归集（七项校验 E-33） */
    @Audited(module = "cost", action = "create_item", targetType = "cost_item", targetId = "{0.costNo}")
    @Transactional
    public Long createItem(CostItem item) {
        boolean valid = false;
        for (String type : SEVEN_TYPES) {
            if (type.equals(item.getCostType())) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw BizException.badRequest("成本类型必须是七项之一（E-33）：MATERIAL/LABOR/OUTSOURCE/EXPENSE/SITE/QUALITY/MGMT");
        }
        if (item.getAmount() == null || item.getAmount().signum() <= 0) {
            throw BizException.badRequest("金额必填且 >0");
        }
        item.setCostNo(codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "CI:" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")),
                "CI-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-", 6)));
        if (item.getOccurredDate() == null) {
            item.setOccurredDate(LocalDate.now());
        }
        itemMapper.insert(item);
        return item.getId();
    }

    /** FR-COST-02：七项合计校验（合计=总成本，误差 ≤1 分） */
    public Map<String, Object> summaryByType(Long projectId) {
        List<CostItem> items = itemMapper.selectList(new LambdaQueryWrapper<CostItem>()
                .eq(CostItem::getProjectId, projectId));
        Map<String, BigDecimal> byType = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CostItem item : items) {
            byType.merge(item.getCostType(), item.getAmount(), BigDecimal::add);
            total = total.add(item.getAmount());
        }
        for (String type : SEVEN_TYPES) {
            byType.putIfAbsent(type, BigDecimal.ZERO);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("byType", byType);
        result.put("total", total);
        result.put("itemCount", items.size());
        return result;
    }

    /** FR-COST-07：超支预警（10% 通知 / 20% 红灯） */
    public Map<String, Object> overrunCheck(Long projectId) {
        Map<String, Object> result = new HashMap<>();
        List<CostBudget> budgets = budgetMapper.selectList(new LambdaQueryWrapper<CostBudget>()
                .eq(CostBudget::getProjectId, projectId)
                .eq(CostBudget::getStatus, "APPROVED"));
        BigDecimal budgetTotal = budgets.stream()
                .map(CostBudget::getBudgetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> summary = summaryByType(projectId);
        BigDecimal actual = (BigDecimal) summary.get("total");
        result.put("budget", budgetTotal);
        result.put("actual", actual);
        if (budgetTotal.signum() == 0) {
            result.put("level", "NO_BUDGET");
            return result;
        }
        BigDecimal overrunPct = actual.subtract(budgetTotal)
                .divide(budgetTotal, 4, RoundingMode.HALF_UP);
        result.put("overrunPct", overrunPct);
        if (overrunPct.compareTo(new BigDecimal("0.20")) >= 0) {
            result.put("level", "RED");
            result.put("message", "超支 ≥20%，红灯（UC-COST-04）");
        } else if (overrunPct.compareTo(new BigDecimal("0.10")) >= 0) {
            result.put("level", "YELLOW");
            result.put("message", "超支 ≥10%，通知 PM+财务（UC-COST-03）");
        } else {
            result.put("level", "GREEN");
        }
        return result;
    }

    /** FR-COST-05：EVM — SPI=EV/PV，CPI=EV/AC */
    @Transactional
    public CostEvm computeEvm(Long projectId, LocalDate date, BigDecimal pv, BigDecimal ev, BigDecimal ac) {
        CostEvm evm = new CostEvm();
        evm.setProjectId(projectId);
        evm.setSnapshotDate(date);
        evm.setPv(pv);
        evm.setEv(ev);
        evm.setAc(ac);
        if (pv != null && pv.signum() > 0) {
            evm.setSpi(ev.divide(pv, 4, RoundingMode.HALF_UP));
        }
        if (ac != null && ac.signum() > 0) {
            evm.setCpi(ev.divide(ac, 4, RoundingMode.HALF_UP));
        }
        evmMapper.insert(evm);
        return evm;
    }

    /** FR-COST-10：成本快照（每日 02:00 job 入口；唯一：项目+日） */
    @Audited(module = "cost", action = "snapshot", targetType = "cost_snapshot", targetId = "{0.projectId}")
    @Transactional
    public CostSnapshot snapshot(Long projectId, LocalDate date) {
        Map<String, Object> summary = summaryByType(projectId);
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> byType = (Map<String, BigDecimal>) summary.get("byType");
        CostSnapshot snapshot = new CostSnapshot();
        snapshot.setProjectId(projectId);
        snapshot.setSnapshotDate(date);
        snapshot.setMaterialCost(byType.get("MATERIAL"));
        snapshot.setLaborCost(byType.get("LABOR"));
        snapshot.setOutsourceCost(byType.get("OUTSOURCE"));
        snapshot.setTravelCost(byType.get("EXPENSE"));
        snapshot.setSiteCost(byType.get("SITE"));
        snapshot.setQualityCost(byType.get("QUALITY"));
        snapshot.setMgmtCost(byType.get("MGMT"));
        snapshot.setTotalCost((BigDecimal) summary.get("total"));
        snapshotMapper.insert(snapshot);
        return snapshot;
    }
}
