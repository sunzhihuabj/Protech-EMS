package com.projlife.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.project.entity.PrjTask;
import com.projlife.project.entity.PrjTaskDependency;
import com.projlife.project.mapper.PrjTaskDependencyMapper;
import com.projlife.project.mapper.PrjTaskMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FR-PRJ-22 关键路径算法（CPM）：前推求 ES/EF → 后推求 LS/LF → 松弛=0 即关键路径。
 * 依赖类型 FS（完成-开始），lagDays 偏移。
 */
@Service
public class CpmService {

    private final PrjTaskMapper taskMapper;
    private final PrjTaskDependencyMapper depMapper;

    public CpmService(PrjTaskMapper taskMapper, PrjTaskDependencyMapper depMapper) {
        this.taskMapper = taskMapper;
        this.depMapper = depMapper;
    }

    /** CPM 计算结果 */
    public static class CpmResult {
        public List<TaskCpm> tasks;
        public List<Long> criticalPathIds;
        public int totalDurationDays;
    }

    public static class TaskCpm {
        public Long taskId;
        public String taskName;
        public String wbsCode;
        public int durationDays;
        public LocalDate es; // earliest start
        public LocalDate ef; // earliest finish
        public LocalDate ls; // latest start
        public LocalDate lf; // latest finish
        public int slack; // = LS - ES（天），0 即关键路径
        public boolean critical;
    }

    /** 计算项目关键路径 */
    public CpmResult calculate(Long projectId) {
        List<PrjTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PrjTask>()
                .eq(PrjTask::getProjectId, projectId)
                .eq(PrjTask::getDeleted, 0));
        if (tasks.isEmpty()) {
            throw BizException.of(ErrorCode.NOT_FOUND, "项目无任务");
        }

        List<PrjTaskDependency> deps = depMapper.selectList(new LambdaQueryWrapper<PrjTaskDependency>()
                .in(PrjTaskDependency::getTaskId,
                        tasks.stream().map(PrjTask::getId).collect(Collectors.toList())));

        // 构建依赖图：taskId → 前置任务列表
        Map<Long, List<PrjTaskDependency>> depGraph = deps.stream()
                .collect(Collectors.groupingBy(PrjTaskDependency::getTaskId));

        // task Map
        Map<Long, PrjTask> taskMap = tasks.stream()
                .collect(Collectors.toMap(PrjTask::getId, t -> t));

        // 拓扑排序（Kahn）
        Map<Long, Integer> inDegree = new HashMap<>();
        tasks.forEach(t -> inDegree.put(t.getId(), 0));
        for (PrjTaskDependency dep : deps) {
            inDegree.merge(dep.getTaskId(), 1, Integer::sum);
        }
        Queue<Long> queue = new LinkedList<>();
        tasks.forEach(t -> { if (inDegree.getOrDefault(t.getId(), 0) == 0) queue.add(t.getId()); });
        List<PrjTask> sorted = new ArrayList<>();
        Map<Long, List<Long>> children = new HashMap<>();
        for (PrjTaskDependency dep : deps) {
            children.computeIfAbsent(dep.getPreTaskId(), k -> new ArrayList<>()).add(dep.getTaskId());
        }
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            PrjTask t = taskMap.get(id);
            if (t != null) sorted.add(t);
            for (Long child : children.getOrDefault(id, Collections.emptyList())) {
                int remain = inDegree.merge(child, -1, Integer::sum);
                if (remain == 0) queue.add(child);
            }
        }
        if (sorted.size() < tasks.size()) {
            throw BizException.of(ErrorCode.PARAM_INVALID, "任务依赖存在环，无法计算关键路径");
        }

        // 工期（天）
        Map<Long, Integer> duration = new HashMap<>();
        for (PrjTask t : tasks) {
            int d = 1;
            if (t.getPlanStart() != null && t.getPlanEnd() != null) {
                d = Math.max(1, (int) ChronoUnit.DAYS.between(t.getPlanStart(), t.getPlanEnd()));
            }
            duration.put(t.getId(), d);
        }

        // 前推：ES/EF
        Map<Long, LocalDate> es = new HashMap<>();
        Map<Long, LocalDate> ef = new HashMap<>();
        for (PrjTask t : sorted) {
            LocalDate start = t.getPlanStart() != null ? t.getPlanStart() : LocalDate.now();
            List<PrjTaskDependency> taskDeps = depGraph.getOrDefault(t.getId(), Collections.emptyList());
            for (PrjTaskDependency dep : taskDeps) {
                LocalDate preE = ef.getOrDefault(dep.getPreTaskId(), start);
                LocalDate candidate = preE.plusDays(dep.getLagDays() == null ? 0 : dep.getLagDays());
                if (candidate.isAfter(start)) start = candidate;
            }
            es.put(t.getId(), start);
            ef.put(t.getId(), start.plusDays(duration.get(t.getId())));
        }

        // 项目总工期
        LocalDate projectEnd = ef.values().stream().max(LocalDate::compareTo).orElse(LocalDate.now());

        // 后推：LF/LS
        Map<Long, LocalDate> lf = new HashMap<>();
        Map<Long, LocalDate> ls = new HashMap<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            PrjTask t = sorted.get(i);
            LocalDate finish = projectEnd;
            List<Long> taskChildren = children.getOrDefault(t.getId(), Collections.emptyList());
            for (Long child : taskChildren) {
                LocalDate childLs = ls.getOrDefault(child, projectEnd);
                if (childLs.isBefore(finish)) finish = childLs;
            }
            lf.put(t.getId(), finish);
            ls.put(t.getId(), finish.minusDays(duration.get(t.getId())));
        }

        // 结果组装
        CpmResult result = new CpmResult();
        List<TaskCpm> cpms = new ArrayList<>();
        List<Long> criticalIds = new ArrayList<>();
        for (PrjTask t : sorted) {
            TaskCpm cpm = new TaskCpm();
            cpm.taskId = t.getId();
            cpm.taskName = t.getTaskName();
            cpm.wbsCode = t.getWbsCode();
            cpm.durationDays = duration.get(t.getId());
            cpm.es = es.get(t.getId());
            cpm.ef = ef.get(t.getId());
            cpm.lf = lf.get(t.getId());
            cpm.ls = ls.get(t.getId());
            long slackDays = ChronoUnit.DAYS.between(cpm.ls, cpm.es);
            cpm.slack = (int) slackDays;
            cpm.critical = slackDays == 0;
            if (cpm.critical) criticalIds.add(t.getId());
            cpms.add(cpm);
        }
        result.tasks = cpms;
        result.criticalPathIds = criticalIds;
        result.totalDurationDays = (int) ChronoUnit.DAYS.between(
                es.values().stream().min(LocalDate::compareTo).orElse(LocalDate.now()),
                projectEnd);
        return result;
    }
}
