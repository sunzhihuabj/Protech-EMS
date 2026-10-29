package com.projlife.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.project.entity.PrjTask;
import com.projlife.project.entity.PrjTaskDependency;
import com.projlife.project.mapper.PrjTaskDependencyMapper;
import com.projlife.project.mapper.PrjTaskMapper;
import com.projlife.org.service.audit.Audited;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务与 WBS（05 册 §4.2）：状态机、开工前置校验（BR-PRJ-02）、依赖成环检测、日期顺延、我的任务。
 */
@Service
public class TaskService {

    private final PrjTaskMapper taskMapper;
    private final PrjTaskDependencyMapper dependencyMapper;
    private final ProjectService projectService;

    public TaskService(PrjTaskMapper taskMapper, PrjTaskDependencyMapper dependencyMapper,
                       ProjectService projectService) {
        this.taskMapper = taskMapper;
        this.dependencyMapper = dependencyMapper;
        this.projectService = projectService;
    }

    public PageResp<PrjTask> myTasks(PageReq pageReq) {
        Long userId = UserContextHolder.currentUserId();
        Page<PrjTask> page = taskMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()),
                new LambdaQueryWrapper<PrjTask>()
                        .eq(PrjTask::getAssigneeId, userId)
                        .orderByAsc(PrjTask::getPlanEnd));
        PageResp<PrjTask> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    public List<PrjTaskDependency> dependenciesOf(Long taskId) {
        return dependencyMapper.selectList(new LambdaQueryWrapper<PrjTaskDependency>()
                .eq(PrjTaskDependency::getTaskId, taskId));
    }

    /** FR-PRJ-21：新增依赖（含成环检测，异常处理表：提示循环路径并拒绝） */
    @Audited(module = "project", action = "add_dependency", targetType = "prj_task_dependency", targetId = "{0.taskId}")
    @Transactional
    public void addDependency(PrjTaskDependency dep) {
        PrjTask task = mustGet(dep.getTaskId());
        mustGet(dep.getPreTaskId());
        if (dep.getTaskId().equals(dep.getPreTaskId())) {
            throw BizException.badRequest("任务不能依赖自身");
        }
        String cycle = findCycle(dep.getTaskId(), dep.getPreTaskId(), true);
        if (cycle != null) {
            throw BizException.badRequest("存在循环依赖：" + cycle + "，请调整依赖关系");
        }
        dep.setDepType(dep.getDepType() == null ? "FS" : dep.getDepType());
        dependencyMapper.insert(dep);
    }

    /** DFS：从 pre 沿后继能否回到 task（含本次新增边 pre→task），返回环路径 */
    private String findCycle(Long taskId, Long preTaskId, boolean includeProposed) {
        Map<Long, List<Long>> successors = new HashMap<>();
        for (PrjTaskDependency d : dependencyMapper.selectList(new LambdaQueryWrapper<PrjTaskDependency>())) {
            successors.computeIfAbsent(d.getPreTaskId(), k -> new ArrayList<>()).add(d.getTaskId());
        }
        if (includeProposed) {
            successors.computeIfAbsent(preTaskId, k -> new ArrayList<>()).add(taskId);
        }
        List<Long> path = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        path.add(preTaskId);
        if (dfs(preTaskId, taskId, successors, visited, path)) {
            StringBuilder sb = new StringBuilder();
            for (Long id : path) {
                sb.append(id).append("→");
            }
            return sb.append(taskId).toString();
        }
        return null;
    }

    private boolean dfs(Long current, Long target, Map<Long, List<Long>> successors,
                        Set<Long> visited, List<Long> path) {
        if (current.equals(target)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        for (Long next : successors.getOrDefault(current, new ArrayList<>())) {
            path.add(next);
            if (dfs(next, target, successors, visited, path)) {
                return true;
            }
            path.remove(path.size() - 1);
        }
        return false;
    }

    /** FR-PRJ-14 + BR-PRJ-02：开工——前置未完成拒绝（不是提示后允许） */
    @Audited(module = "project", action = "start_task", targetType = "prj_task", targetId = "{0}")
    @Transactional
    public void start(Long taskId) {
        PrjTask task = mustGet(taskId);
        if (!"TODO".equals(task.getStatus()) && !"DELAYED".equals(task.getStatus())) {
            throw BizException.badRequest("仅未开始/已延期任务可开工（当前 " + task.getStatus() + "）");
        }
        List<PrjTaskDependency> deps = dependencyMapper.selectList(new LambdaQueryWrapper<PrjTaskDependency>()
                .eq(PrjTaskDependency::getTaskId, taskId));
        for (PrjTaskDependency dep : deps) {
            PrjTask pre = taskMapper.selectById(dep.getPreTaskId());
            if (pre != null && !"DONE".equals(pre.getStatus()) && !"CANCELLED".equals(pre.getStatus())) {
                throw BizException.badRequest("前置任务「" + pre.getTaskName() + "」未完成，不可开工（BR-PRJ-02）");
            }
        }
        task.setStatus("DOING");
        task.setActualStart(LocalDateTime.now());
        taskMapper.updateById(task);
        projectService.markInProgress(task.getProjectId());
    }

    /** FR-PRJ-16/17：完工——产出物强制 + 审批流转 */
    @Audited(module = "project", action = "finish_task", targetType = "prj_task", targetId = "{0}")
    @Transactional
    public void finish(Long taskId, List<Long> outputFileIds, String finishNote) {
        PrjTask task = mustGet(taskId);
        if (!"DOING".equals(task.getStatus())) {
            throw BizException.badRequest("仅进行中任务可完工");
        }
        if (task.getOutputRequired() == 1 && (outputFileIds == null || outputFileIds.isEmpty())) {
            throw BizException.badRequest("该任务要求上传产出物（FR-PRJ-16）");
        }
        task.setFinishNote(finishNote);
        if (task.getApprovalRequired() == 1) {
            task.setStatus("PENDING_APPROVAL");
            if (outputFileIds != null) {
                task.setOutputFileIds(toJsonArray(outputFileIds));
            }
            task.setApproveBy(UserContextHolder.currentUserId());
            taskMapper.updateById(task);
            return;
        }
        complete(task, outputFileIds);
    }

    /** FR-PRJ-17：审批通过/驳回 */
    @Transactional
    public void reviewTask(Long taskId, boolean approved, String reason) {
        PrjTask task = mustGet(taskId);
        if (!"PENDING_APPROVAL".equals(task.getStatus())) {
            throw BizException.badRequest("仅待审批任务可审");
        }
        if (approved) {
            complete(task, null);
        } else {
            if (reason == null || reason.length() < 4) {
                throw BizException.badRequest("驳回必须填理由（FR-PRJ-17）");
            }
            task.setStatus("DOING");
            task.setPauseReason("驳回：" + reason);
            taskMapper.updateById(task);
        }
    }

    private void complete(PrjTask task, List<Long> outputFileIds) {
        task.setStatus("DONE");
        task.setActualEnd(LocalDateTime.now());
        task.setProgressPct(new java.math.BigDecimal("100"));
        if (outputFileIds != null && !outputFileIds.isEmpty()) {
            task.setOutputFileIds(toJsonArray(outputFileIds));
        }
        taskMapper.updateById(task);
    }

    /** FR-PRJ-18：逾期标记（job 每日调用入口） */
    @Transactional
    public int markDelayed() {
        List<PrjTask> overdue = taskMapper.selectList(new LambdaQueryWrapper<PrjTask>()
                .lt(PrjTask::getPlanEnd, LocalDate.now())
                .in(PrjTask::getStatus, "TODO", "DOING"));
        int count = 0;
        for (PrjTask task : overdue) {
            task.setStatus("DELAYED");
            task.setDelayDays((int) (LocalDate.now().toEpochDay() - task.getPlanEnd().toEpochDay()));
            taskMapper.updateById(task);
            count++;
        }
        return count;
    }

    /** FR-PRJ-10/22：日期调整——后续依赖任务自动顺延（FS 简化模型：end 前移/后移联动） */
    @Audited(module = "project", action = "adjust_task_date", targetType = "prj_task", targetId = "{0}")
    @Transactional
    public int adjustDates(Long taskId, LocalDate newEnd) {
        PrjTask task = mustGet(taskId);
        int shift = (int) (newEnd.toEpochDay() - task.getPlanEnd().toEpochDay());
        task.setPlanEnd(newEnd);
        if (shift > 0) {
            task.setPlanStart(task.getPlanStart().plusDays(shift));
        }
        taskMapper.updateById(task);
        if (shift != 0) {
            return shiftDependents(task.getId(), shift, new HashSet<>());
        }
        return 0;
    }

    private int shiftDependents(Long taskId, int shift, Set<Long> visited) {
        int count = 0;
        for (PrjTaskDependency dep : dependencyMapper.selectList(new LambdaQueryWrapper<PrjTaskDependency>()
                .eq(PrjTaskDependency::getPreTaskId, taskId))) {
            if (!visited.add(dep.getTaskId())) {
                continue;
            }
            PrjTask next = taskMapper.selectById(dep.getTaskId());
            if (next == null) {
                continue;
            }
            next.setPlanStart(next.getPlanStart().plusDays(shift));
            next.setPlanEnd(next.getPlanEnd().plusDays(shift));
            taskMapper.updateById(next);
            count++;
            count += shiftDependents(next.getId(), shift, visited);
        }
        return count;
    }

    /** FR-PRJ-25：部门负载（同人同期任务天数 / 工作日天数） */
    public List<Map<String, Object>> deptLoad(Long deptId, LocalDate start, LocalDate end) {
        List<PrjTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PrjTask>()
                .eq(PrjTask::getDeptId, deptId)
                .le(PrjTask::getPlanStart, end)
                .ge(PrjTask::getPlanEnd, start)
                .in(PrjTask::getStatus, "TODO", "DOING", "DELAYED"));
        Map<Long, List<PrjTask>> byUser = new HashMap<>();
        for (PrjTask task : tasks) {
            if (task.getAssigneeId() != null) {
                byUser.computeIfAbsent(task.getAssigneeId(), k -> new ArrayList<>()).add(task);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        long workDays = Math.max(1, countWorkdays(start, end));
        for (Map.Entry<Long, List<PrjTask>> entry : byUser.entrySet()) {
            double load = 0;
            for (PrjTask task : entry.getValue()) {
                load += countWorkdays(max(task.getPlanStart(), start), min(task.getPlanEnd(), end));
            }
            Map<String, Object> row = new HashMap<>();
            row.put("userId", entry.getKey());
            row.put("taskCount", entry.getValue().size());
            row.put("loadPct", Math.round(load / workDays * 100));
            row.put("overloaded", load / workDays > 1.0);
            rows.add(row);
        }
        return rows;
    }

    private long countWorkdays(LocalDate start, LocalDate end) {
        long days = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                days++;
            }
        }
        return days;
    }

    private LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private String toJsonArray(List<Long> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(ids.get(i)).append('"');
        }
        return sb.append(']').toString();
    }

    private PrjTask mustGet(Long id) {
        PrjTask task = taskMapper.selectById(id);
        if (task == null) {
            throw BizException.notFound("任务不存在");
        }
        return task;
    }
}
