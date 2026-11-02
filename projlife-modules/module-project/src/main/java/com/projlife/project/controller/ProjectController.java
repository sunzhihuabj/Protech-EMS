package com.projlife.project.controller;

import com.projlife.common.api.ApiResponse;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.project.entity.PrjEco;
import com.projlife.project.entity.PrjMember;
import com.projlife.project.entity.PrjProject;
import com.projlife.project.entity.PrjTask;
import com.projlife.project.entity.PrjTaskDependency;
import com.projlife.project.entity.PrjTemplate;
import com.projlife.project.mapper.PrjTemplateMapper;
import com.projlife.project.service.EcoService;
import com.projlife.project.service.ProjectService;
import com.projlife.project.service.TaskService;
import com.projlife.security.auth.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 项目接口（05 册 §3 页面清单）。
 */
@RestController
@RequestMapping("/api/v1/project")
public class ProjectController {

    private final ProjectService projectService;
    private final com.projlife.project.service.CpmService cpmService;
    private final TaskService taskService;
    private final EcoService ecoService;
    private final PrjTemplateMapper templateMapper;

    public ProjectController(ProjectService projectService, com.projlife.project.service.CpmService cpmService, TaskService taskService,
                             EcoService ecoService, PrjTemplateMapper templateMapper) {
        this.projectService = projectService;
        this.cpmService = cpmService;
        this.taskService = taskService;
        this.ecoService = ecoService;
        this.templateMapper = templateMapper;
    }

    @GetMapping("/templates")
    public ApiResponse<List<PrjTemplate>> templates() {
        return ApiResponse.ok(templateMapper.selectList(null));
    }

    @GetMapping
    @RequirePermission("project:view")
    public ApiResponse<PageResp<PrjProject>> page(PageReq pageReq,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(projectService.page(pageReq, keyword, status));
    }

    @GetMapping("/{id}")
    @RequirePermission("project:view")
    public ApiResponse<PrjProject> detail(@PathVariable Long id) {
        return ApiResponse.ok(projectService.detail(id));
    }

    @PostMapping
    @RequirePermission("project:create")
    public ApiResponse<Long> create(@RequestBody PrjProject project,
                                    @RequestParam(required = false) Long templateId) {
        return ApiResponse.ok(projectService.manualCreate(project, templateId));
    }

    @GetMapping("/{id}/tasks")
    @RequirePermission("project:view")
    public ApiResponse<List<PrjTask>> taskTree(@PathVariable Long id) {
        return ApiResponse.ok(projectService.taskTree(id));
    }

    @PostMapping("/tasks/{taskId}/start")
    @RequirePermission("project:view")
    public ApiResponse<Void> startTask(@PathVariable Long taskId) {
        taskService.start(taskId);
        return ApiResponse.ok();
    }

    @PostMapping("/tasks/{taskId}/finish")
    @RequirePermission("project:view")
    public ApiResponse<Void> finishTask(@PathVariable Long taskId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) body.get("outputFileIds");
        List<Long> outputFileIds = null;
        if (rawIds != null) {
            outputFileIds = new java.util.ArrayList<>();
            for (Object rawId : rawIds) {
                outputFileIds.add(Long.valueOf(String.valueOf(rawId)));
            }
        }
        taskService.finish(taskId, outputFileIds, (String) body.get("finishNote"));
        return ApiResponse.ok();
    }

    @PostMapping("/tasks/{taskId}/review")
    @RequirePermission("project:gate:review")
    public ApiResponse<Void> reviewTask(@PathVariable Long taskId, @RequestBody Map<String, Object> body) {
        taskService.reviewTask(taskId, Boolean.TRUE.equals(body.get("approved")), (String) body.get("reason"));
        return ApiResponse.ok();
    }

    @PostMapping("/tasks/{taskId}/dependencies")
    @RequirePermission("project:task:adjust_date")
    public ApiResponse<Void> addDependency(@PathVariable Long taskId, @RequestBody PrjTaskDependency dep) {
        dep.setTaskId(taskId);
        taskService.addDependency(dep);
        return ApiResponse.ok();
    }

    /** FR-PRJ-10/22：日期调整（自动顺延后续依赖任务） */
    @PostMapping("/tasks/{taskId}/adjust-date")
    @RequirePermission("project:task:adjust_date")
    public ApiResponse<Integer> adjustDate(@PathVariable Long taskId, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(taskService.adjustDates(taskId, LocalDate.parse(body.get("planEnd"))));
    }

    /** FR-PRJ-24：我的任务 */
    @GetMapping("/my-tasks")
    @RequirePermission("project:view")
    public ApiResponse<PageResp<PrjTask>> myTasks(PageReq pageReq) {
        return ApiResponse.ok(taskService.myTasks(pageReq));
    }

    /** FR-PRJ-25：部门负载 */
    @GetMapping("/load")
    @RequirePermission("project:view")
    public ApiResponse<List<Map<String, Object>>> deptLoad(@RequestParam Long deptId,
                                                           @RequestParam String start,
                                                           @RequestParam String end) {
        return ApiResponse.ok(taskService.deptLoad(deptId, LocalDate.parse(start), LocalDate.parse(end)));
    }

    /** FR-PRJ-38/39：门控 */
    @GetMapping("/{id}/gate/{stageTaskId}")
    @RequirePermission("project:view")
    public ApiResponse<Map<String, Object>> gateStatus(@PathVariable Long id, @PathVariable Long stageTaskId) {
        return ApiResponse.ok(projectService.gateStatus(id, stageTaskId));
    }

    @PostMapping("/gate/{stageTaskId}/pass")
    @RequirePermission("project:gate:review")
    public ApiResponse<Void> passGate(@PathVariable Long stageTaskId) {
        projectService.passGate(stageTaskId);
        return ApiResponse.ok();
    }

    // ---------- ECO ----------

    @GetMapping("/{id}/ecos")
    @RequirePermission("project:view")
    public ApiResponse<List<PrjEco>> ecos(@PathVariable Long id) {
        return ApiResponse.ok(ecoService.listByProject(id));
    }

    /** BR-09：未附旧料处置方案禁止提交 */
    @PostMapping("/{id}/ecos")
    @RequirePermission("project:eco:create")
    public ApiResponse<Long> createEco(@PathVariable Long id, @RequestBody PrjEco eco) {
        eco.setProjectId(id);
        return ApiResponse.ok(ecoService.create(eco));
    }

    @PostMapping("/ecos/{ecoId}/approve")
    @RequirePermission("project:eco:approve")
    public ApiResponse<Void> approveEco(@PathVariable Long ecoId, @RequestBody Map<String, Object> body) {
        ecoService.approve(ecoId, Boolean.TRUE.equals(body.get("approved")));
        return ApiResponse.ok();
    }

    @GetMapping("/ecos/{ecoId}/affected-tasks")
    @RequirePermission("project:view")
    public ApiResponse<List<PrjTask>> affectedTasks(@RequestParam Long projectId) {
        return ApiResponse.ok(ecoService.affectedTasks(projectId));
    }

    // ---------- 成员 ----------

    @GetMapping("/{id}/members")
    @RequirePermission("project:view")
    public ApiResponse<List<PrjMember>> members(@PathVariable Long id) {
        return ApiResponse.ok(projectService.membersOf(id));
    }

    @PostMapping("/{id}/members")
    @RequirePermission("project:member:manage")
    public ApiResponse<Long> addMember(@PathVariable Long id, @RequestBody PrjMember member) {
        member.setProjectId(id);
        return ApiResponse.ok(projectService.addMember(member));
    }

    @PostMapping("/{id}/members/{userId}/remove")
    @RequirePermission("project:member:manage")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId);
        return ApiResponse.ok();
    }

    /** FR-PRJ-22 关键路径算法（CPM） */
    @GetMapping("/{id}/critical-path")
    @RequirePermission("project:view")
    public ApiResponse<com.projlife.project.service.CpmService.CpmResult> criticalPath(@PathVariable Long id) {
        return ApiResponse.ok(cpmService.calculate(id));
    }
}
