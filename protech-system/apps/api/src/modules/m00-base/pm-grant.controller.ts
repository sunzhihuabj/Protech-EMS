import { Body, Controller, Delete, Get, Post, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { BizException } from '../../shared/biz.exception';
import { AuditService } from '../../shared/audit.service';
import { NotifyService } from '../../shared/notify.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { parseId, parseIdOrNull } from '../../shared/serialize';

/**
 * FR-M00-003 R-PM 项目级临时授权：
 * 按项目给负责人该项目数据全量只读+有限操作权；项目关闭后 7 天自动回收（worker）；
 * 指派与回收全程审计。
 */
@Controller('pm-grants')
export class PmGrantController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
    private readonly notify: NotifyService,
  ) {}

  @Get()
  async list(@Query() q: Record<string, string>) {
    const grants = await this.prisma.projectPmGrant.findMany({
      where: {
        revokedAt: null,
        ...(q.userId ? { userId: parseId(q.userId) } : {}),
        ...(q.projectId ? { projectId: parseId(q.projectId) } : {}),
      },
      orderBy: { id: 'desc' },
      take: 200,
    });
    return grants.map((g) => ({
      id: g.id.toString(),
      userId: g.userId.toString(),
      projectId: g.projectId.toString(),
      grantedAt: g.grantedAt,
      expiredAt: g.expiredAt,
      revokedAt: g.revokedAt,
    }));
  }

  @Post()
  @RequirePermission('sys:pmgrant:manage')
  async grant(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.userId || !body.projectId) throw BizException.param('userId/projectId 必填');
    const userId = parseId(body.userId);
    const projectId = parseId(body.projectId);
    const project = await this.prisma.project.findFirst({ where: { id: projectId, deletedAt: null } });
    if (!project) throw BizException.notFound('项目不存在');
    const user = await this.prisma.sysUser.findFirst({ where: { id: userId, deletedAt: null } });
    if (!user) throw BizException.notFound('用户不存在');

    const existing = await this.prisma.projectPmGrant.findUnique({
      where: { userId_projectId: { userId, projectId } },
    });
    if (existing && !existing.revokedAt) return { id: existing.id.toString() };

    const grant = existing
      ? await this.prisma.projectPmGrant.update({
          where: { id: existing.id },
          data: { revokedAt: null, expiredAt: parseIdOrNull(body.expiredAt) ? new Date(String(body.expiredAt)) : null, grantedAt: new Date(), grantedBy: me.id },
        })
      : await this.prisma.projectPmGrant.create({
          data: { userId, projectId, grantedBy: me.id },
        });

    await this.prisma.sysUserRole
      .create({ data: { userId, roleId: await this.pmRoleId(), grantedBy: me.id } })
      .catch(() => undefined); // 已有 R-PM 角色则忽略
    await this.audit.log({ action: 'grant', objectType: 'ProjectPmGrant', objectId: grant.id, after: { userId: userId.toString(), projectId: projectId.toString() } });
    await this.notify.send({
      userId,
      type: 'remind',
      title: `您已被授权查看项目 ${project.projectCode} ${project.standardName}`,
      bizType: 'project',
      bizId: projectId.toString(),
    });
    return { id: grant.id.toString() };
  }

  @Delete()
  @RequirePermission('sys:pmgrant:manage')
  async revoke(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.userId || !body.projectId) throw BizException.param('userId/projectId 必填');
    const userId = parseId(body.userId);
    const projectId = parseId(body.projectId);
    const updated = await this.prisma.projectPmGrant.updateMany({
      where: { userId, projectId, revokedAt: null },
      data: { revokedAt: new Date() },
    });
    await this.audit.log({ action: 'revoke', objectType: 'ProjectPmGrant', objectId: `${userId}:${projectId}` });
    // 若该用户无任何在授项目且无其他角色来源，回收 R-PM 角色在 me 可见性上自然消失（角色保留不删，避免误伤）
    return { revoked: updated.count };
  }

  private async pmRoleId(): Promise<bigint> {
    const role = await this.prisma.sysRole.findFirst({ where: { code: 'R-PM', deletedAt: null } });
    if (!role) throw BizException.validation('R-PM 角色未初始化');
    return role.id;
  }
}
