package com.projlife.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.mes.entity.ApsCapacity;
import com.projlife.mes.entity.ApsMaterialCheck;
import com.projlife.mes.entity.ApsSchedule;
import com.projlife.mes.entity.ApsScheduleDetail;
import com.projlife.mes.entity.MesProcessOrder;
import com.projlife.mes.entity.MesProductionOrder;
import com.projlife.mes.mapper.ApsCapacityMapper;
import com.projlife.mes.mapper.ApsMaterialCheckMapper;
import com.projlife.mes.mapper.ApsScheduleDetailMapper;
import com.projlife.mes.mapper.ApsScheduleMapper;
import com.projlife.mes.mapper.MesProcessOrderMapper;
import com.projlife.mes.mapper.MesProductionOrderMapper;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.security.auth.RequirePermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * APS 排产服务（V4-04 M06 T-APS，S5 BACKLOG 落地）：
 * L1 粗排（产能正向顺排 + 资源负载冲突检测）→ L2 细排调整（齐套校验 + 人工拖拽）→ 发布。
 *
 * <p>算法（A-33 简化口径）：工序按 生产订单优先级→订单号→工序序 逐道顺排；
 * 每道工序选当前负载最小的 EQUIPMENT 资源，planned_start = max(资源空闲点, 前道完工)，
 * 单日产能超载记冲突不阻塞；齐套缺料记冲突（L2 调整解除后可发布）。</p>
 */
@Service
public class ApsService {

    /** 单资源默认日产能工时上限（A-33：aps_capacity.capacity_per_day 为日工时） */
    private static final int MINUTES_PER_DAY = 8 * 60;

    private final ApsScheduleMapper scheduleMapper;
    private final ApsScheduleDetailMapper detailMapper;
    private final ApsCapacityMapper capacityMapper;
    private final ApsMaterialCheckMapper materialCheckMapper;
    private final MesProductionOrderMapper productionOrderMapper;
    private final MesProcessOrderMapper processOrderMapper;
    private final CodeSequenceService codeSequenceService;

    public ApsService(ApsScheduleMapper scheduleMapper, ApsScheduleDetailMapper detailMapper,
                      ApsCapacityMapper capacityMapper, ApsMaterialCheckMapper materialCheckMapper,
                      MesProductionOrderMapper productionOrderMapper, MesProcessOrderMapper processOrderMapper,
                      CodeSequenceService codeSequenceService) {
        this.scheduleMapper = scheduleMapper;
        this.detailMapper = detailMapper;
        this.capacityMapper = capacityMapper;
        this.materialCheckMapper = materialCheckMapper;
        this.productionOrderMapper = productionOrderMapper;
        this.processOrderMapper = processOrderMapper;
        this.codeSequenceService = codeSequenceService;
    }

    /** 产能登记（EQUIPMENT/PERSON/MOLD，日产能为工时） */
    @RequirePermission("aps:schedule")
    @Transactional
    public ApsCapacity createCapacity(ApsCapacity capacity) {
        capacityMapper.insert(capacity);
        return capacity;
    }

    /** L1 顺排：返回排产单 + 冲突清单（产能超载/缺料），不阻塞生成 */
    @RequirePermission("aps:schedule")
    @Transactional
    public Map<String, Object> scheduleL1(List<Long> productionOrderIds, LocalDate startDate) {
        if (productionOrderIds == null || productionOrderIds.isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "生产订单清单必填");
        }
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        List<ApsCapacity> capacities = capacityMapper.selectList(new LambdaQueryWrapper<ApsCapacity>()
                .eq(ApsCapacity::getResourceType, "EQUIPMENT").eq(ApsCapacity::getStatus, "ACTIVE"));
        if (capacities.isEmpty()) {
            throw BizException.blocked("APS-NO-CAPACITY",
                    "无可用产能资源，请先维护设备产能", "请先在产能台账登记 EQUIPMENT 资源");
        }

        ApsSchedule schedule = new ApsSchedule();
        schedule.setScheduleNo(codeSequenceService.next(CodeTypes.apsSchedule(
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")))));
        schedule.setScheduleVersion(1);
        schedule.setScheduleType("MASTER");
        schedule.setStartDate(startDate);
        schedule.setStatus("CALCULATING");
        scheduleMapper.insert(schedule);

        Map<Long, LocalDateTime> resourceFreeAt = new HashMap<>();
        Map<Long, Integer> resourceMinutesUsed = new HashMap<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        LocalDateTime prevEnd = null;

        List<MesProductionOrder> orders = new ArrayList<>();
        for (Long id : productionOrderIds) {
            MesProductionOrder order = productionOrderMapper.selectById(id);
            if (order == null) {
                throw BizException.notFound("生产订单不存在: " + id);
            }
            orders.add(order);
        }
        // 优先级小者先排，同级按 id
        orders.sort((a, b) -> {
            int pa = a.getPriority() == null ? 5 : a.getPriority();
            int pb = b.getPriority() == null ? 5 : b.getPriority();
            return pa != pb ? Integer.compare(pa, pb) : Long.compare(a.getId(), b.getId());
        });

        for (MesProductionOrder order : orders) {
            // L2 齐套预检：缺料记冲突
            Map<String, Object> material = materialCheck(order.getId());
            if (Boolean.FALSE.equals(material.get("enough"))) {
                conflicts.add(conflict(order.getId(), null, "MATERIAL_SHORT",
                        "齐套缺料 " + material.get("shortLines") + " 行"));
            }
            if (!materialCheckMapper.selectList(new LambdaQueryWrapper<ApsMaterialCheck>()
                    .eq(ApsMaterialCheck::getProductionOrderId, order.getId())
                    .eq(ApsMaterialCheck::getScheduleId, schedule.getId())).isEmpty()) {
                // 该订单本次排产已有齐套快照行，避免重复登记
            }
            List<MesProcessOrder> processes = processOrderMapper.selectList(
                    new LambdaQueryWrapper<MesProcessOrder>()
                            .eq(MesProcessOrder::getProductionOrderId, order.getId())
                            .orderByAsc(MesProcessOrder::getProcessSeq));
            for (MesProcessOrder process : processes) {
                int runMinutes = process.getStandardHours() == null ? 60
                        : process.getStandardHours().multiply(new BigDecimal(60))
                        .setScale(0, RoundingMode.HALF_UP).intValue();
                // 最小负载资源
                ApsCapacity best = null;
                long bestLoad = Long.MAX_VALUE;
                for (ApsCapacity capacity : capacities) {
                    long load = resourceMinutesUsed.getOrDefault(capacity.getResourceId(), 0);
                    if (load < bestLoad) {
                        bestLoad = load;
                        best = capacity;
                    }
                }
                LocalDateTime freeAt = resourceFreeAt.getOrDefault(best.getResourceId(),
                        startDate.atTime(8, 0));
                LocalDateTime plannedStart = prevEnd != null && prevEnd.isAfter(freeAt) ? prevEnd : freeAt;
                LocalDateTime plannedEnd = plannedStart.plusMinutes(runMinutes);

                // 产能冲突：资源当日累计超载记冲突（不阻塞顺排）
                int dayUsed = resourceMinutesUsed.getOrDefault(best.getResourceId(), 0) % MINUTES_PER_DAY;
                int capacityPerDay = best.getCapacityPerDay() == null ? MINUTES_PER_DAY
                        : best.getCapacityPerDay().multiply(new BigDecimal(60))
                        .setScale(0, RoundingMode.HALF_UP).intValue();
                if (dayUsed + runMinutes > capacityPerDay) {
                    conflicts.add(conflict(order.getId(), process.getId(), "CAPACITY_OVER",
                            "资源 " + best.getResourceId() + " 日产能超载（+" + runMinutes + " 分钟）"));
                }

                ApsScheduleDetail detail = new ApsScheduleDetail();
                detail.setScheduleId(schedule.getId());
                detail.setProductionOrderId(order.getId());
                detail.setProcessOrderId(process.getId());
                detail.setResourceId(best.getResourceId());
                detail.setPlannedStart(plannedStart);
                detail.setPlannedEnd(plannedEnd);
                detail.setRunMinutes(runMinutes);
                detail.setStatus("PLANNED");
                detailMapper.insert(detail);

                resourceFreeAt.put(best.getResourceId(), plannedEnd);
                resourceMinutesUsed.merge(best.getResourceId(), runMinutes, Integer::sum);
                prevEnd = plannedEnd;
            }
        }
        schedule.setEndDate(prevEnd == null ? startDate : prevEnd.toLocalDate());
        scheduleMapper.updateById(schedule);

        Map<String, Object> result = new HashMap<>();
        result.put("scheduleId", schedule.getId());
        result.put("scheduleNo", schedule.getScheduleNo());
        result.put("status", schedule.getStatus());
        result.put("detailCount", detailMapper.selectCount(new LambdaQueryWrapper<ApsScheduleDetail>()
                .eq(ApsScheduleDetail::getScheduleId, schedule.getId())));
        result.put("conflicts", conflicts);
        result.put("conflictCount", conflicts.size());
        return result;
    }

    /** 排产明细清单 */
    @RequirePermission("aps:schedule")
    public List<ApsScheduleDetail> detailsOf(Long scheduleId) {
        return detailMapper.selectList(new LambdaQueryWrapper<ApsScheduleDetail>()
                .eq(ApsScheduleDetail::getScheduleId, scheduleId)
                .eq(ApsScheduleDetail::getDeleted, 0)
                .orderByAsc(ApsScheduleDetail::getPlannedStart));
    }

    /** L2 人工调整：拖动工时/资源 → 排产单置 ADJUSTED、版本 +1 */
    @RequirePermission("aps:schedule")
    @Transactional
    public ApsScheduleDetail adjust(Long detailId, LocalDateTime plannedStart, LocalDateTime plannedEnd) {
        ApsScheduleDetail detail = detailMapper.selectById(detailId);
        if (detail == null) {
            throw BizException.notFound("排产明细不存在: " + detailId);
        }
        ApsSchedule schedule = scheduleMapper.selectById(detail.getScheduleId());
        if ("PUBLISHED".equals(schedule.getStatus())) {
            throw BizException.blocked("APS-PUBLISHED",
                    "已发布排产不可直接调整", "请先作废并重排（版本管理）");
        }
        if (plannedStart != null) {
            detail.setPlannedStart(plannedStart);
        }
        if (plannedEnd != null) {
            detail.setPlannedEnd(plannedEnd);
        }
        if (!detail.getPlannedEnd().isAfter(detail.getPlannedStart())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        detailMapper.updateById(detail);
        schedule.setStatus("ADJUSTED");
        schedule.setScheduleVersion(schedule.getScheduleVersion() + 1);
        scheduleMapper.updateById(schedule);
        return detail;
    }

    /** 发布：存在 MATERIAL_SHORT 冲突时阻断（齐套方可上线） */
    @RequirePermission("aps:schedule")
    @Transactional
    public ApsSchedule publish(Long scheduleId) {
        ApsSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw BizException.notFound("排产单不存在: " + scheduleId);
        }
        if ("PUBLISHED".equals(schedule.getStatus())) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "排产单已发布");
        }
        List<ApsMaterialCheck> shorts = materialCheckMapper.selectList(
                new LambdaQueryWrapper<ApsMaterialCheck>()
                        .eq(ApsMaterialCheck::getIsReady, 0)
                        .eq(ApsMaterialCheck::getDeleted, 0));
        if (!shorts.isEmpty()) {
            throw BizException.blocked("APS-MATERIAL-SHORT",
                    "存在齐套缺料记录 " + shorts.size() + " 条，不得发布", "请先补料或在 L2 调整后复核");
        }
        schedule.setStatus("PUBLISHED");
        scheduleMapper.updateById(schedule);
        return schedule;
    }

    /** 齐套校验（L2 输入）：aps_material_check 未就绪行数（is_ready=0） */
    Map<String, Object> materialCheck(Long productionOrderId) {
        long shortLines = materialCheckMapper.selectCount(new LambdaQueryWrapper<ApsMaterialCheck>()
                .eq(ApsMaterialCheck::getProductionOrderId, productionOrderId)
                .eq(ApsMaterialCheck::getIsReady, 0)
                .eq(ApsMaterialCheck::getDeleted, 0));
        Map<String, Object> result = new HashMap<>();
        result.put("enough", shortLines == 0);
        result.put("shortLines", shortLines);
        return result;
    }

    private Map<String, Object> conflict(Long productionOrderId, Long processOrderId,
                                         String type, String message) {
        Map<String, Object> conflict = new HashMap<>();
        conflict.put("productionOrderId", productionOrderId);
        conflict.put("processOrderId", processOrderId);
        conflict.put("type", type);
        conflict.put("message", message);
        return conflict;
    }
}
