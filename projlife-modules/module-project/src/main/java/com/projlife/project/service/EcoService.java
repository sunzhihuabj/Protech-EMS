package com.projlife.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.project.entity.PrjEco;
import com.projlife.project.entity.PrjTask;
import com.projlife.project.mapper.PrjEcoMapper;
import com.projlife.project.mapper.PrjTaskMapper;
import com.projlife.org.service.audit.Audited;
import com.projlife.org.service.code.CodeSequenceService;
import com.projlife.org.service.code.CodeTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工程变更 ECO（05 册 §4.5 + BR-PRJ-06/07）。
 * BR-09：未附旧料处置方案禁止提交；批准后生效并通知受影响任务责任人。
 */
@Service
public class EcoService {

    private final PrjEcoMapper ecoMapper;
    private final PrjTaskMapper taskMapper;
    private final CodeSequenceService codeSequenceService;

    public EcoService(PrjEcoMapper ecoMapper, PrjTaskMapper taskMapper,
                      CodeSequenceService codeSequenceService) {
        this.ecoMapper = ecoMapper;
        this.taskMapper = taskMapper;
        this.codeSequenceService = codeSequenceService;
    }

    public List<PrjEco> listByProject(Long projectId) {
        return ecoMapper.selectList(new LambdaQueryWrapper<PrjEco>()
                .eq(PrjEco::getProjectId, projectId)
                .orderByDesc(PrjEco::getId));
    }

    /** FR-PRJ-31：发起 ECO——BR-09 硬卡点（旧料处置方案必填） */
    @Audited(module = "project", action = "create_eco", targetType = "prj_eco", targetId = "{0.changeContent}")
    @Transactional
    public Long create(PrjEco eco) {
        if (eco.getProjectId() == null || eco.getChangeReason() == null || eco.getChangeContent() == null) {
            throw BizException.badRequest("项目/变更原因/变更内容必填（FR-PRJ-31）");
        }
        String disposal = eco.getOldDisposalPlan();
        if (disposal == null || disposal.trim().length() < 10) {
            throw BizException.of(ErrorCode.BLOCKED, "BOM 变更未附旧料处置方案，拒绝提交")
                    .with("blockCode", "BR-09")
                    .with("suggestion", "请填写旧料处置方案（优先领用/工程售后利用/改制/折价出售/报废/客户消化）");
        }
        eco.setEcoNo(codeSequenceService.next(CodeTypes.ecn(
                String.valueOf(LocalDate.now().getYear()))));
        eco.setStatus("SUBMITTED");
        ecoMapper.insert(eco);
        return eco.getId();
    }

    /** FR-PRJ-33/35：审批——批准即生效（生效时间+状态），驳回终止 */
    @Audited(module = "project", action = "approve_eco", targetType = "prj_eco", targetId = "{0}")
    @Transactional
    public void approve(Long ecoId, boolean approved) {
        PrjEco eco = ecoMapper.selectById(ecoId);
        if (eco == null) {
            throw BizException.notFound("ECO 不存在");
        }
        if ("EFFECTED".equals(eco.getStatus()) || "REJECTED".equals(eco.getStatus())) {
            throw BizException.badRequest("ECO 已终态，禁止重复审批");
        }
        if (approved) {
            eco.setStatus("EFFECTED");
            eco.setEffectiveAt(LocalDateTime.now());
        } else {
            eco.setStatus("REJECTED");
        }
        ecoMapper.updateById(eco);
    }

    /** FR-PRJ-35：受影响的下游任务（按项目当前进行中/未开始任务，简化口径=全部未完成任务） */
    public List<PrjTask> affectedTasks(Long projectId) {
        return taskMapper.selectList(new LambdaQueryWrapper<PrjTask>()
                .eq(PrjTask::getProjectId, projectId)
                .in(PrjTask::getStatus, "TODO", "DOING", "DELAYED"));
    }
}
