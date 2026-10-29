package com.projlife.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.project.entity.PrjTask;
import com.projlife.project.entity.PrjTaskDependency;
import com.projlife.project.entity.PrjTemplate;
import com.projlife.project.entity.PrjTemplateTask;
import com.projlife.project.mapper.PrjTaskDependencyMapper;
import com.projlife.project.mapper.PrjTaskMapper;
import com.projlife.project.mapper.PrjTemplateTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板实例化（FR-PRJ-05/07/08）：模板树 → WBS 任务 + 工期顺排 + 阶段内/跨阶段 FS 依赖。
 * 结构：阶段任务（汇总节点）→ 子任务；阶段内子任务顺序 FS，跨阶段首任务依赖上一阶段末任务。
 */
@Service
public class TemplateInstantiateService {

    private final PrjTemplateTaskMapper templateTaskMapper;
    private final PrjTaskMapper taskMapper;
    private final PrjTaskDependencyMapper dependencyMapper;
    private final com.projlife.org.service.code.CodeSequenceService codeSequenceService;

    public TemplateInstantiateService(PrjTemplateTaskMapper templateTaskMapper, PrjTaskMapper taskMapper,
                                      PrjTaskDependencyMapper dependencyMapper,
                                      com.projlife.org.service.code.CodeSequenceService codeSequenceService) {
        this.templateTaskMapper = templateTaskMapper;
        this.taskMapper = taskMapper;
        this.dependencyMapper = dependencyMapper;
        this.codeSequenceService = codeSequenceService;
    }

    @Transactional
    public int instantiate(com.projlife.project.entity.PrjProject project, PrjTemplate template, Long managerId) {
        List<PrjTemplateTask> rows = templateTaskMapper.selectList(new LambdaQueryWrapper<PrjTemplateTask>()
                .eq(PrjTemplateTask::getTemplateId, template.getId())
                .orderByAsc(PrjTemplateTask::getSeq));
        List<PrjTemplateTask> stages = new ArrayList<>();
        Map<Long, List<PrjTemplateTask>> childrenByStage = new HashMap<>();
        for (PrjTemplateTask row : rows) {
            if (row.getParentId() == null) {
                stages.add(row);
            } else {
                childrenByStage.computeIfAbsent(row.getParentId(), k -> new ArrayList<>()).add(row);
            }
        }

        LocalDate cursor = project.getPlanStart();
        Long lastChildOfPreviousStage = null;
        int created = 0;
        int stageIdx = 0;

        for (PrjTemplateTask stage : stages) {
            stageIdx++;
            // 1) 阶段汇总任务（日期先占位，子任务展开后回填）
            PrjTask stageTask = newTask(project, null, stage.getTaskName(), "NORMAL");
            stageTask.setWbsCode(String.valueOf(stageIdx));
            stageTask.setTaskNo(nextTaskNo(project, stageIdx, 0));
            stageTask.setPlanStart(cursor);
            stageTask.setPlanEnd(cursor);
            taskMapper.insert(stageTask);
            created++;

            // 2) 阶段内子任务：顺序 FS + 工期顺排
            Long prevChild = null;
            int childIdx = 0;
            List<PrjTemplateTask> children = childrenByStage.getOrDefault(stage.getId(), new ArrayList<>());
            for (PrjTemplateTask tpl : children) {
                childIdx++;
                PrjTask task = newTask(project, stageTask.getId(), tpl.getTaskName(),
                        tpl.getApprovalRequired() == 1 ? "APPROVAL" : "NORMAL");
                task.setDeptId(tpl.getDeptId());
                task.setRoleCode(tpl.getRoleCode());
                task.setIsOptional(tpl.getIsOptional());
                task.setPlanStart(cursor);
                task.setPlanEnd(cursor.plusDays(Math.max(tpl.getDurationDays(), 1) - 1));
                task.setOutputRequired(tpl.getOutputRequired());
                task.setApprovalRequired(tpl.getApprovalRequired());
                task.setWbsCode(stageIdx + "." + childIdx);
                task.setTaskNo(nextTaskNo(project, stageIdx, childIdx));
                taskMapper.insert(task);
                created++;

                if (prevChild != null) {
                    addDependency(task.getId(), prevChild, "FS", 0);
                } else if (lastChildOfPreviousStage != null) {
                    // 跨阶段：本阶段首任务 ← 上一阶段末任务（FS）
                    addDependency(task.getId(), lastChildOfPreviousStage, "FS", 0);
                }
                prevChild = task.getId();
                cursor = task.getPlanEnd().plusDays(1);
            }

            // 3) 回填阶段汇总日期
            if (prevChild != null) {
                PrjTask lastChild = taskMapper.selectById(prevChild);
                stageTask.setPlanEnd(lastChild.getPlanEnd());
                taskMapper.updateById(stageTask);
                lastChildOfPreviousStage = prevChild;
            } else {
                stageTask.setPlanEnd(cursor.minusDays(1));
                taskMapper.updateById(stageTask);
            }
        }
        return created;
    }

    private PrjTask newTask(com.projlife.project.entity.PrjProject project, Long parentId,
                            String name, String type) {
        PrjTask task = new PrjTask();
        task.setProjectId(project.getId());
        task.setParentId(parentId);
        task.setTaskName(name);
        task.setTaskType(type);
        task.setStatus("TODO");
        task.setWbsCode("0");
        return task;
    }

    /** 任务号 T-{project_no 后 4 位}-{3 位}（05 册；非 18 类编码，走序列服务按项目 scope 取号） */
    private String nextTaskNo(com.projlife.project.entity.PrjProject project, int stageIdx, int childIdx) {
        String tail = project.getProjectNo();
        tail = tail.length() >= 4 ? tail.substring(tail.length() - 4) : tail;
        return codeSequenceService.next(new com.projlife.org.service.code.CodeTypes.CodeRequest(
                "TASK:" + project.getProjectNo(), "T-" + tail + "-", 3));
    }

    private void addDependency(Long taskId, Long preTaskId, String type, int lag) {
        PrjTaskDependency dep = new PrjTaskDependency();
        dep.setTaskId(taskId);
        dep.setPreTaskId(preTaskId);
        dep.setDepType(type);
        dep.setLagDays(lag);
        dependencyMapper.insert(dep);
    }
}
