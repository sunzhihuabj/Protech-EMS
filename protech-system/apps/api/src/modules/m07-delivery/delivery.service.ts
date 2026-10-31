import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { BizException } from '../../shared/biz.exception';
import { parseId, parseIdOrNull } from '../../shared/serialize';

/** E-派工状态机 */
const DISPATCH_TRANSITIONS: Record<string, string[]> = {
  draft: ['confirmed', 'in_progress'],
  confirmed: ['in_progress'],
  in_progress: ['done'],
  done: [],
};

/** 里程碑模板（FR-M04-004：签约→方案交底→生产完成→发货→进场安装→调试验收→结算回款） */
export const MILESTONE_TEMPLATE = ['签约', '方案交底', '生产完成', '发货', '进场安装', '调试验收', '结算回款'];

/**
 * M07 工程交付（FR-M07-001~007 S4 集）+ M04 里程碑（FR-M04-004）。
 * BR-04：派工单不选风场不许提交。
 */
@Injectable()
export class DeliveryService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  // ---------------- 派工 ----------------

  async createDispatch(body: Record<string, unknown>, userId: bigint) {
    const projectId = parseIdOrNull(body.projectId);
    const windFarmId = parseIdOrNull(body.windFarmId);
    if (!projectId || !windFarmId) {
      // BR-04 服务端硬卡点
      throw BizException.br('BR-04', '该订单尚未关联风场，请补充风场信息或申请新建风场档案');
    }
    const project = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!project) throw BizException.notFound('项目不存在');
    const farm = await this.prisma.windFarm.findFirst({ where: { id: windFarmId, deletedAt: null } });
    if (!farm) throw BizException.notFound('风场不存在');

    const pgCode = await this.codes.next('dispatch');
    const dispatch = await this.prisma.dispatch.create({
      data: {
        pgCode,
        projectId,
        windFarmId,
        teamId: parseIdOrNull(body.teamId),
        members: JSON.stringify(Array.isArray(body.members) ? body.members : []),
        planDays: body.planDays != null ? Number(body.planDays) : null,
        source: String(body.source ?? 'manual'),
        shipMoveId: parseIdOrNull(body.shipMoveId),
        taskDesc: (body.taskDesc as string) ?? null,
        createdBy: userId,
        updatedBy: userId,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'Dispatch', objectId: dispatch.id, after: { pgCode, projectId: projectId.toString() } });
    return dispatch;
  }

  /** FR-M07-002 发货自动生成派工单草稿（M06 发货流水触发；带项目与风场） */
  async autoDraftFromShip(projectId: bigint, shipMoveId: bigint, userId: bigint) {
    const project = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!project) throw BizException.notFound('项目不存在');
    if (!project.windFarmId) {
      // 排产两段式未回填风场 → 草稿缺风场，需人工确认补选（BR-04 不满足不发布）
      await this.notify.send({
        userId: project.pmUserId ?? project.engOwnerId ?? project.createdBy ?? userId,
        type: 'todo',
        title: `项目 ${project.projectCode} 已发货但未关联风场，派工草稿待补选风场（BR-04）`,
        bizType: 'project',
        bizId: project.id.toString(),
      });
      return null;
    }
    const existing = await this.prisma.dispatch.findFirst({
      where: { projectId, shipMoveId, deletedAt: null },
    });
    if (existing) return existing; // 幂等
    return this.createDispatch(
      {
        projectId,
        windFarmId: project.windFarmId,
        source: 'auto_ship',
        shipMoveId,
        taskDesc: `发货流水 ${shipMoveId} 自动生成（工程部确认派工）`,
      },
      userId,
    );
  }

  async dispatchTransition(dispatchId: bigint, to: string, userId: bigint) {
    const d = await this.prisma.dispatch.findFirst({ where: { id: dispatchId, deletedAt: null } });
    if (!d) throw BizException.notFound('派工单不存在');
    const allowed = DISPATCH_TRANSITIONS[d.status] ?? [];
    if (!allowed.includes(to)) throw BizException.validation(`状态不允许：${d.status} → ${to}`);
    await this.prisma.dispatch.update({ where: { id: dispatchId }, data: { status: to, updatedBy: userId } });
    await this.audit.log({ action: 'dispatch_status', objectType: 'Dispatch', objectId: dispatchId, before: { status: d.status }, after: { status: to } });
    return { status: to };
  }

  // ---------------- 里程碑（FR-M04-004） ----------------

  async initMilestones(projectId: bigint, userId: bigint, planDates?: Record<string, string>) {
    const existing = await this.prisma.milestone.count({ where: { projectId, deletedAt: null } });
    if (existing > 0) throw BizException.validation('里程碑已初始化');
    const rows = [];
    for (const [i, name] of MILESTONE_TEMPLATE.entries()) {
      const planDate = planDates?.[name] ? new Date(planDates[name]) : null;
      rows.push({ projectId, name, sort: i, planDate, ownerId: null, createdBy: userId, updatedBy: userId });
    }
    await this.prisma.milestone.createMany({ data: rows });
    await this.audit.log({ action: 'milestone_init', objectType: 'Milestone', objectId: projectId, after: { count: rows.length } });
    return this.milestonesOf(projectId);
  }

  async milestonesOf(projectId: bigint) {
    const list = await this.prisma.milestone.findMany({
      where: { projectId, deletedAt: null },
      orderBy: { sort: 'asc' },
    });
    const now = Date.now();
    return list.map((m) => ({
      ...m,
      id: m.id.toString(),
      projectId: m.projectId.toString(),
      overdue: m.status === 'pending' && m.planDate !== null && m.planDate.getTime() < now,
    }));
  }

  async completeMilestone(milestoneId: bigint, userId: bigint) {
    const m = await this.prisma.milestone.findFirst({ where: { id: milestoneId, deletedAt: null } });
    if (!m) throw BizException.notFound('里程碑不存在');
    const updated = await this.prisma.milestone.update({
      where: { id: milestoneId },
      data: { status: 'done', actualDate: new Date(), updatedBy: userId },
    });
    // 发货里程碑完成 → 项目状态守卫提示（不自动迁移，由项目状态机执行）
    await this.audit.log({ action: 'milestone_done', objectType: 'Milestone', objectId: milestoneId, before: { status: m.status }, after: { status: 'done' } });
    void updated;
    return { status: 'done' };
  }

  // ---------------- 交底 / 日志 / 问题 ----------------

  async createDisclosure(body: Record<string, unknown>, userId: bigint) {
    const projectId = parseIdOrNull(body.projectId);
    if (!projectId) throw BizException.param('projectId 必填');
    const row = await this.prisma.disclosure.create({
      data: {
        projectId,
        type: String(body.type ?? 'plan'),
        discloseAt: body.discloseAt ? new Date(String(body.discloseAt)) : new Date(),
        attendees: JSON.stringify(Array.isArray(body.attendees) ? body.attendees : []),
        content: (body.content as string) ?? null,
        createdBy: userId,
      },
    });
    await this.audit.log({ action: 'create', objectType: 'Disclosure', objectId: row.id });
    return row;
  }

  async createSiteLog(body: Record<string, unknown>, userId: bigint) {
    const projectId = parseIdOrNull(body.projectId);
    if (!projectId || !body.content) throw BizException.param('projectId/content 必填');
    const row = await this.prisma.siteLog.create({
      data: {
        projectId,
        dispatchId: parseIdOrNull(body.dispatchId),
        authorId: userId,
        content: String(body.content),
        photos: JSON.stringify(Array.isArray(body.photos) ? body.photos : []),
        logDate: body.logDate ? new Date(String(body.logDate)) : new Date(),
      },
    });
    return row;
  }

  async createSiteIssue(body: Record<string, unknown>, userId: bigint) {
    const projectId = parseIdOrNull(body.projectId);
    if (!projectId || !body.description || !body.ownerId || !body.deadline) {
      throw BizException.param('projectId/description/ownerId/deadline 必填');
    }
    const row = await this.prisma.siteIssue.create({
      data: {
        projectId,
        description: String(body.description),
        ownerId: parseId(body.ownerId),
        deadline: new Date(String(body.deadline)),
        createdBy: userId,
      },
    });
    return row;
  }

  async closeSiteIssue(issueId: bigint, userId: bigint) {
    const issue = await this.prisma.siteIssue.findFirst({ where: { id: issueId } });
    if (!issue) throw BizException.notFound('问题不存在');
    if (issue.status === 'closed') throw BizException.validation('问题已闭环');
    await this.prisma.siteIssue.update({ where: { id: issueId }, data: { status: 'closed', closedAt: new Date() } });
    await this.audit.log({ action: 'issue_close', objectType: 'SiteIssue', objectId: issueId });
    return { status: 'closed' };
  }
}
