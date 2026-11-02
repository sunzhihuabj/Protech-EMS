package com.projlife.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.cost.entity.KbMetricDef;
import com.projlife.cost.entity.KbMetricSnapshot;
import com.projlife.cost.mapper.KbMetricDefMapper;
import com.projlife.cost.mapper.KbMetricSnapshotMapper;
import com.projlife.security.auth.RequirePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 看板指标服务（V4-05 M12 T-KB-01/02，S8 BACKLOG 落地）：
 * 指标口径定义 + 快照落库 + 阈值灯色判定 + 期间趋势下钻。
 *
 * <p>灯色口径（A-35）：target 存在时按完成率 value/target 判定——≥1 GREEN、
 * ≥yellowRatio(默认 0.9) YELLOW、其余 RED；threshold_json 可覆盖 yellowRatio/redRatio。</p>
 */
@Service
public class KbMetricService {

    private final KbMetricDefMapper defMapper;
    private final KbMetricSnapshotMapper snapshotMapper;
    private final javax.sql.DataSource dataSource;

    public KbMetricService(KbMetricDefMapper defMapper, KbMetricSnapshotMapper snapshotMapper,
                           javax.sql.DataSource dataSource) {
        this.defMapper = defMapper;
        this.snapshotMapper = snapshotMapper;
        this.dataSource = dataSource;
    }

    /** 指标口径定义 */
    @RequirePermission("kb:metric:def")
    @Transactional
    public KbMetricDef createDef(KbMetricDef def) {
        if (def.getMetricCode() == null || def.getMetricCode().isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "指标编码必填");
        }
        if (def.getMetricName() == null || def.getBoardId() == null) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "指标名称与所属看板必填");
        }
        Long exists = defMapper.selectCount(new LambdaQueryWrapper<KbMetricDef>()
                .eq(KbMetricDef::getMetricCode, def.getMetricCode())
                .eq(KbMetricDef::getDeleted, 0));
        if (exists != null && exists > 0) {
            throw BizException.conflict("指标编码已存在: " + def.getMetricCode());
        }
        defMapper.insert(def);
        return def;
    }

    /** 指标快照：按口径判灯色；同期重复即修正（UK 兜底） */
    @RequirePermission("kb:metric:def")
    @Transactional
    public KbMetricSnapshot snapshot(String metricCode, String period, BigDecimal value) {
        KbMetricDef def = defMapper.selectOne(new LambdaQueryWrapper<KbMetricDef>()
                .eq(KbMetricDef::getMetricCode, metricCode)
                .eq(KbMetricDef::getDeleted, 0).last("LIMIT 1"));
        if (def == null) {
            throw BizException.notFound("指标未定义: " + metricCode);
        }
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "期间格式必须为 yyyy-MM");
        }
        KbMetricSnapshot snapshot = new KbMetricSnapshot();
        snapshot.setMetricCode(metricCode);
        snapshot.setPeriod(period);
        snapshot.setMetricValue(value);
        snapshot.setTargetValue(def.getTarget());
        snapshot.setStatus(judge(def, value));
        KbMetricSnapshot existing = snapshotMapper.selectOne(new LambdaQueryWrapper<KbMetricSnapshot>()
                .eq(KbMetricSnapshot::getMetricCode, metricCode)
                .eq(KbMetricSnapshot::getPeriod, period).last("LIMIT 1"));
        if (existing != null) {
            snapshot.setId(existing.getId());
            snapshotMapper.updateById(snapshot);
            return snapshot;
        }
        snapshotMapper.insert(snapshot);
        return snapshot;
    }

    /** 趋势下钻：指标近 N 期快照 + 达标率 */
    @RequirePermission("kb:metric:def")
    public Map<String, Object> trend(String metricCode, int limitPeriods) {
        defMapper.selectOne(new LambdaQueryWrapper<KbMetricDef>()
                .eq(KbMetricDef::getMetricCode, metricCode)
                .eq(KbMetricDef::getDeleted, 0).last("LIMIT 1"));
        List<KbMetricSnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<KbMetricSnapshot>()
                        .eq(KbMetricSnapshot::getMetricCode, metricCode)
                        .eq(KbMetricSnapshot::getDeleted, 0)
                        .orderByDesc(KbMetricSnapshot::getPeriod)
                        .last(limitPeriods > 0 ? "LIMIT " + Math.min(limitPeriods, 24) : "LIMIT 12"));
        long green = snapshots.stream().filter(s -> "GREEN".equals(s.getStatus())).count();
        Map<String, Object> result = new HashMap<>();
        result.put("metricCode", metricCode);
        result.put("periods", snapshots);
        result.put("greenRate", snapshots.isEmpty() ? BigDecimal.ZERO
                : new BigDecimal(green * 100).divide(new BigDecimal(snapshots.size()), 2, RoundingMode.HALF_UP));
        return result;
    }

    /** 灯色判定（A-35 完成率口径，threshold_json 覆盖比例） */
    String judge(KbMetricDef def, BigDecimal value) {
        if (def.getTarget() == null || def.getTarget().signum() == 0 || value == null) {
            return "GREEN"; // 无目标值不判灯（口径未定义不误报）
        }
        BigDecimal yellowRatio = new BigDecimal("0.9");
        BigDecimal redRatio = new BigDecimal("0.8");
        if (def.getThresholdJson() != null && def.getThresholdJson().contains("yellowRatio")) {
            try {
                Map<?, ?> thresholds = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(def.getThresholdJson(), Map.class);
                if (thresholds.get("yellowRatio") != null) {
                    yellowRatio = new BigDecimal(String.valueOf(thresholds.get("yellowRatio")));
                }
                if (thresholds.get("redRatio") != null) {
                    redRatio = new BigDecimal(String.valueOf(thresholds.get("redRatio")));
                }
            } catch (Exception ignore) {
                // 口径 JSON 非法时用默认比例
            }
        }
        BigDecimal ratio = value.divide(def.getTarget(), 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.ONE) >= 0) {
            return "GREEN";
        }
        return ratio.compareTo(yellowRatio) >= 0 ? "YELLOW" : "RED";
    }

    /**
     * SPRINT-KPI1（F7）：九板指标自动取数——已验证列名的 11 项指标逐码计算并快照（UK 幂等修正口径）。
     * 单指标失败不阻塞其余（取数失败→快照缺失→看板显示 —，不误报）。
     */
    @Transactional
    public Map<String, Object> autoFetchAll(String period) {
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        Map<String, java.math.BigDecimal> catalog = new LinkedHashMap<>();
        try {
            catalog.put("S17.MO.DELIVERY", ratio(jdbc,
                    "SELECT COUNT(*) FROM mes_production_order WHERE deleted=0 AND status='COMPLETED'",
                    "SELECT COUNT(*) FROM mes_production_order WHERE deleted=0"));
            catalog.put("MES.FINISH.RATE", ratio(jdbc,
                    "SELECT COALESCE(SUM(finished_qty),0) FROM mes_production_order WHERE deleted=0",
                    "SELECT COALESCE(SUM(plan_qty),0) FROM mes_production_order WHERE deleted=0"));
            catalog.put("QMS.FIRSTPASS", ratio(jdbc,
                    "SELECT COUNT(*) FROM qms_inspection WHERE deleted=0 AND result='PASS' AND defect_count=0",
                    "SELECT COUNT(*) FROM qms_inspection WHERE deleted=0"));
            // 跨库可移植：日期边界在 Java 端算好作字面量（DATE_FORMAT/CURDATE/DATE_SUB 为 MySQL 专属）
            String monthStart = period + "-01";
            String monthEnd = java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).toString();
            String slowBefore = java.time.LocalDate.now().minusDays(180).toString();
            catalog.put("CT.AMOUNT.MONTH", single(jdbc,
                    "SELECT COALESCE(SUM(amount),0)/10000 FROM ct_contract WHERE deleted=0 "
                            + "AND created_at >= '" + monthStart + "' AND created_at < '" + monthEnd + "'"));
            catalog.put("CT.COUNT.MONTH", single(jdbc,
                    "SELECT COUNT(*) FROM ct_contract WHERE deleted=0 AND created_at >= '" + monthStart
                            + "' AND created_at < '" + monthEnd + "'"));
            catalog.put("PRJ.PROGRESS.AVG", single(jdbc,
                    "SELECT COALESCE(AVG(progress_pct),0) FROM prj_project WHERE deleted=0"));
            catalog.put("PRJ.OVERDUE.COUNT", single(jdbc,
                    "SELECT COUNT(*) FROM prj_project WHERE deleted=0 AND plan_end < CURRENT_DATE "
                            + "AND status NOT IN ('COMPLETED','CLOSED','CANCELLED')"));
            catalog.put("INV.SLOW.RATE", ratio(jdbc,
                    "SELECT COUNT(*) FROM inv_batch WHERE deleted=0 AND qty>0 AND receipt_date < '" + slowBefore + "'",
                    "SELECT COUNT(*) FROM inv_batch WHERE deleted=0 AND qty>0"));
            catalog.put("KB.CARD.RED.COUNT", single(jdbc,
                    "SELECT COUNT(*) FROM kb_card WHERE deleted=0 AND status IN ('RED','BLOCKED','ESCALATED')"));
            catalog.put("KB.CARD.CLOSE.RATE", ratio(jdbc,
                    "SELECT COUNT(*) FROM kb_card WHERE deleted=0 AND status='CLOSED'",
                    "SELECT COUNT(*) FROM kb_card WHERE deleted=0 AND status IN ('RED','BLOCKED','ESCALATED','CLOSED')"));
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(KbMetricService.class).warn("指标取数目录构建失败：{}", ex.getMessage());
        }
        int ok = 0;
        int fail = 0;
        for (Map.Entry<String, java.math.BigDecimal> e : catalog.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            try {
                snapshot(e.getKey(), period, e.getValue());
                ok++;
            } catch (Exception ex) {
                fail++;
                org.slf4j.LoggerFactory.getLogger(KbMetricService.class)
                        .warn("指标快照失败 {}={}: {}", e.getKey(), e.getValue(), ex.getMessage());
            }
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("period", period);
        summary.put("fetched", ok);
        summary.put("failed", fail);
        summary.put("catalogSize", catalog.size());
        return summary;
    }

    private java.math.BigDecimal ratio(org.springframework.jdbc.core.JdbcTemplate jdbc, String numSql, String denSql) {
        java.math.BigDecimal num = single(jdbc, numSql);
        java.math.BigDecimal den = single(jdbc, denSql);
        if (num == null || den == null || den.signum() == 0) {
            return null;
        }
        return num.multiply(java.math.BigDecimal.valueOf(100))
                .divide(den, 2, java.math.RoundingMode.HALF_UP);
    }

    private java.math.BigDecimal single(org.springframework.jdbc.core.JdbcTemplate jdbc, String sql) {
        try {
            return jdbc.queryForObject(sql, java.math.BigDecimal.class);
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(KbMetricService.class).warn("取数 SQL 失败: {} -> {}", sql, ex.getMessage());
            return null;
        }
    }
}
