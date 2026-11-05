package com.projlife.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.projlife.common.api.PageReq;
import com.projlife.common.api.PageResp;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.exception.BizException;
import com.projlife.mdm.api.MdmApi;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import com.projlife.project.api.PrjApi;
import com.projlife.project.entity.PrjMember;
import com.projlife.project.entity.PrjProject;
import com.projlife.project.entity.PrjTask;
import com.projlife.project.entity.PrjTemplate;
import com.projlife.project.entity.PrjTemplateTask;
import com.projlife.project.mapper.PrjMemberMapper;
import com.projlife.project.mapper.PrjProjectMapper;
import com.projlife.project.mapper.PrjTaskMapper;
import com.projlife.project.mapper.PrjTemplateMapper;
import com.projlife.project.mapper.PrjTemplateTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行项目（05 册 §4.1/§4.4/§4.6）：自动立项、手动立项、成员、阶段门控。
 * FR-PRJ-01 合同归档即立项；FR-PRJ-05 模板生成任务树；FR-PRJ-28/29 成员权限联动。
 */
@Service
public class ProjectService implements PrjApi {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final PrjProjectMapper projectMapper;
    private final PrjTemplateMapper templateMapper;
    private final PrjTemplateTaskMapper templateTaskMapper;
    private final PrjTaskMapper taskMapper;
    private final PrjMemberMapper memberMapper;
    private final CodeSequenceService codeSequenceService;
    private final TemplateInstantiateService instantiateService;
    private final MdmApi mdmApi;
    private final com.projlife.security.auth.DataScopeGuard dataScopeGuard;

    public ProjectService(PrjProjectMapper projectMapper, PrjTemplateMapper templateMapper,
                          PrjTemplateTaskMapper templateTaskMapper, PrjTaskMapper taskMapper,
                          PrjMemberMapper memberMapper, CodeSequenceService codeSequenceService,
                          TemplateInstantiateService instantiateService, MdmApi mdmApi,
                          com.projlife.security.auth.DataScopeGuard dataScopeGuard) {
        this.projectMapper = projectMapper;
        this.templateMapper = templateMapper;
        this.templateTaskMapper = templateTaskMapper;
        this.taskMapper = taskMapper;
        this.memberMapper = memberMapper;
        this.codeSequenceService = codeSequenceService;
        this.instantiateService = instantiateService;
        this.mdmApi = mdmApi;
        this.dataScopeGuard = dataScopeGuard;
    }

    public PageResp<PrjProject> page(PageReq pageReq, String keyword, String status) {
        LambdaQueryWrapper<PrjProject> wrapper = new LambdaQueryWrapper<PrjProject>()
                .orderByDesc(PrjProject::getId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(PrjProject::getProjectName, keyword);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PrjProject::getStatus, status);
        }
        Page<PrjProject> page = projectMapper.selectPage(new Page<>(pageReq.getPageNum(), pageReq.getPageSize()), wrapper);
        PageResp<PrjProject> resp = new PageResp<>();
        resp.setTotal(page.getTotal());
        resp.setPages(page.getPages());
        resp.setRecords(page.getRecords());
        return resp;
    }

    /** 按 ID 直查数据范围校验（SPRINT-SEC3：SELF/DEPT 档只能看自己/本部门立项的项目） */
    public PrjProject detail(Long id) {
        PrjProject project = mustGet(id);
        dataScopeGuard.checkByOwner(project.getCreatedBy());
        return project;
    }

    public List<PrjTask> taskTree(Long projectId) {
        return taskMapper.selectList(new LambdaQueryWrapper<PrjTask>()
                .eq(PrjTask::getProjectId, projectId)
                .orderByAsc(PrjTask::getWbsCode));
    }

    /** FR-PRJ-01/03/04/05：自动立项（合同 PERFORMING 触发） */
    @Override
    @Audited(module = "project", action = "auto_create_project", targetType = "prj_project", targetId = "{1}")
    @Transactional
    public Long autoCreateFromContract(Long mdProjectId, Long contractId, String projectName,
                                       LocalDate planStart, int defaultDays) {
        if (!mdmApi.projectActive(mdProjectId)) {
            log.warn("自动立项跳过：项目档案不可用 mdProjectId={}", mdProjectId);
            return null;
        }
        return doCreate(mdProjectId, contractId, projectName, null, planStart,
                planStart.plusDays(defaultDays), UserContextHolder.currentUserId(), true);
    }

    /** FR-PRJ-02：手动立项（内部研发/无合同项目） */
    @Audited(module = "project", action = "manual_create_project", targetType = "prj_project", targetId = "{0.projectName}")
    @Transactional
    public Long manualCreate(PrjProject project, Long templateId) {
        if (project.getMdProjectId() == null || project.getProjectName() == null
                || project.getManagerId() == null || project.getPlanStart() == null || project.getPlanEnd() == null) {
            throw BizException.badRequest("项目档案/名称/项目经理/计划起止必填（FR-PRJ-02）");
        }
        return doCreate(project.getMdProjectId(), project.getContractId(), project.getProjectName(),
                templateId, project.getPlanStart(), project.getPlanEnd(), project.getManagerId(), false);
    }

    private Long doCreate(Long mdProjectId, Long contractId, String projectName, Long templateId,
                          LocalDate planStart, LocalDate planEnd, Long managerId, boolean useDefaultTemplate) {
        PrjTemplate template = resolveTemplate(templateId, useDefaultTemplate);
        PrjProject project = new PrjProject();
        project.setProjectNo(codeSequenceService.next(CodeTypes.projectExec(
                String.valueOf(LocalDate.now().getYear()))));
        project.setMdProjectId(mdProjectId);
        project.setContractId(contractId);
        project.setProjectName(projectName);
        project.setManagerId(managerId);
        project.setTemplateId(template == null ? null : template.getId());
        project.setPlanStart(planStart);
        project.setPlanEnd(planEnd);
        project.setStatus("NOT_STARTED");
        projectMapper.insert(project);

        // FR-PRJ-29：经理入成员（PROJECT 数据范围联动基础）
        PrjMember member = new PrjMember();
        member.setProjectId(project.getId());
        member.setUserId(managerId);
        member.setRoleCode("PM");
        member.setJoinDate(LocalDate.now());
        member.setIsActive(1);
        memberMapper.insert(member);

        if (template != null) {
            instantiateService.instantiate(project, template, managerId);
        }
        return project.getId();
    }

    private PrjTemplate resolveTemplate(Long templateId, boolean useDefault) {
        if (templateId != null) {
            return templateMapper.selectById(templateId);
        }
        if (!useDefault) {
            return null;
        }
        return templateMapper.selectOne(new LambdaQueryWrapper<PrjTemplate>()
                .eq(PrjTemplate::getIsDefault, 1)
                .eq(PrjTemplate::getStatus, "ACTIVE")
                .last("LIMIT 1"));
    }

    /** FR-PRJ-28：成员维护 */
    @Audited(module = "project", action = "add_member", targetType = "prj_member", targetId = "{0.userId}")
    @Transactional
    public Long addMember(PrjMember member) {
        mustGet(member.getProjectId());
        member.setIsActive(1);
        member.setJoinDate(LocalDate.now());
        memberMapper.insert(member);
        return member.getId();
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        PrjMember member = memberMapper.selectOne(new LambdaQueryWrapper<PrjMember>()
                .eq(PrjMember::getProjectId, projectId)
                .eq(PrjMember::getUserId, userId)
                .eq(PrjMember::getIsActive, 1)
                .last("LIMIT 1"));
        if (member == null) {
            throw BizException.notFound("成员不在项目中");
        }
        if ("PM".equals(member.getRoleCode())) {
            throw BizException.badRequest("项目经理移交需 R02 审批（FR-PRJ-30），不可直接移除");
        }
        member.setIsActive(0);
        member.setLeaveDate(LocalDate.now());
        memberMapper.updateById(member);
    }

    public List<PrjMember> membersOf(Long projectId) {
        return memberMapper.selectList(new LambdaQueryWrapper<PrjMember>()
                .eq(PrjMember::getProjectId, projectId)
                .eq(PrjMember::getIsActive, 1));
    }

    /** FR-PRJ-38/39 阶段门控：阶段任务全部完成方可提交评审 */
    public Map<String, Object> gateStatus(Long projectId, Long stageTaskId) {
        PrjTask stage = taskMapper.selectById(stageTaskId);
        if (stage == null || stage.getProjectId() == null
                || !stage.getProjectId().equals(projectId)) {
            throw BizException.notFound("阶段任务不存在");
        }
        List<PrjTask> children = taskMapper.selectList(new LambdaQueryWrapper<PrjTask>()
                .eq(PrjTask::getParentId, stageTaskId));
        long notDone = children.stream().filter(t -> !"DONE".equals(t.getStatus())
                && !"CANCELLED".equals(t.getStatus())).count();
        Map<String, Object> result = new HashMap<>();
        result.put("stage", stage.getTaskName());
        result.put("totalChildren", children.size());
        result.put("notDone", notDone);
        result.put("gateOpen", notDone == 0);
        result.put("message", notDone == 0 ? "可提交门控评审" : "仍有 " + notDone + " 个任务未完成，不可开工下一阶段（FR-PRJ-38）");
        return result;
    }

    /** 门控评审通过：项目进度推进（简化：记录审批留痕） */
    @Audited(module = "project", action = "gate_review_pass", targetType = "prj_task", targetId = "{0}")
    @Transactional
    public void passGate(Long stageTaskId) {
        PrjTask stage = taskMapper.selectById(stageTaskId);
        if (stage == null) {
            throw BizException.notFound("阶段任务不存在");
        }
        stage.setStatus("DONE");
        stage.setActualEnd(java.time.LocalDateTime.now());
        taskMapper.updateById(stage);
    }

    /** 首个任务开工时联动：执行项目与项目档案状态推进（02 册 §7.2） */
    @Transactional
    public void markInProgress(Long projectId) {
        PrjProject project = mustGet(projectId);
        if ("NOT_STARTED".equals(project.getStatus())) {
            project.setStatus("IN_PROGRESS");
            project.setActualStart(LocalDate.now());
            projectMapper.updateById(project);
            mdmApi.markMdProjectInProgress(project.getMdProjectId());
        }
    }

    private PrjProject mustGet(Long id) {
        PrjProject project = projectMapper.selectById(id);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        return project;
    }
}
