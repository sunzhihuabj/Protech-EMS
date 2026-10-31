import { Injectable } from '@nestjs/common';
import { PrismaService } from './prisma.service';

/** E-16 数据范围六档的偏序 */
const SCOPE_ORDER: Record<string, number> = {
  self: 1,
  dept: 2,
  chain: 3,
  dept_projects: 3,
  all: 4,
};

export interface ScopeInfo {
  scope: string;
  userId: bigint;
  deptId: bigint | null;
  deptIdsInSubtree: bigint[]; // 本部门（含子部门）用户所在部门集合
  chainCode: string | null;
  /** R-PM 项目级授权的项目 id 集（FR-M00-003） */
  grantedProjectIds: bigint[];
  expiredGrantIds: bigint[]; // 已到期待回收（7 天自动回收由 worker 处理，查询侧立即过滤）
}

/**
 * 数据范围服务（FR-M00-002）。
 * 有效范围 = 用户多角色 maxDataScope 的最大档；R-PM 授权叠加项目集。
 */
@Injectable()
export class ScopeService {
  constructor(private readonly prisma: PrismaService) {}

  async of(userId: bigint): Promise<ScopeInfo> {
    const user = await this.prisma.sysUser.findFirst({
      where: { id: userId, deletedAt: null },
      select: { id: true, deptId: true },
    });
    const roles = await this.prisma.sysUserRole.findMany({
      where: { userId },
      select: { roleId: true },
    });
    const roleRows = roles.length
      ? await this.prisma.sysRole.findMany({
          where: { id: { in: roles.map((r) => r.roleId) }, deletedAt: null },
          select: { maxDataScope: true },
        })
      : [];
    let scope = 'self';
    for (const r of roleRows) {
      const s = r.maxDataScope;
      if ((SCOPE_ORDER[s] ?? 0) > (SCOPE_ORDER[scope] ?? 0)) scope = s;
    }

    // 部门子树：path 前缀匹配
    let deptIdsInSubtree: bigint[] = [];
    let chainCode: string | null = null;
    if (user?.deptId) {
      const dept = await this.prisma.sysDept.findFirst({
        where: { id: user.deptId, deletedAt: null },
        select: { id: true, path: true, chainCode: true },
      });
      if (dept) {
        chainCode = dept.chainCode;
        const subtree = await this.prisma.sysDept.findMany({
          where: {
            deletedAt: null,
            OR: [{ path: { startsWith: dept.path + dept.id + '/' } }, { id: dept.id }],
          },
          select: { id: true },
        });
        deptIdsInSubtree = subtree.map((d) => d.id);
      }
    }

    const grants = await this.prisma.projectPmGrant.findMany({
      where: { userId, revokedAt: null, OR: [{ expiredAt: null }, { expiredAt: { gt: new Date() } }] },
      select: { projectId: true },
    });

    return {
      scope,
      userId,
      deptId: user?.deptId ?? null,
      deptIdsInSubtree,
      chainCode,
      grantedProjectIds: grants.map((g) => g.projectId),
      expiredGrantIds: [],
    };
  }

  /** 项目列表的范围过滤（各模块按自身字段注册过滤片段；S0 先落项目档案口径） */
  async projectWhere(user: { id: bigint }): Promise<Record<string, unknown>> {
    const s = await this.of(user.id);
    const selfCond = {
      OR: [
        { pmUserId: s.userId },
        { engOwnerId: s.userId },
        { salesOwnerId: s.userId },
        { collectorId: s.userId },
        { aftersalesOwnerId: s.userId },
        { createdBy: s.userId },
      ],
    };
    switch (s.scope) {
      case 'all':
        return {};
      case 'chain': {
        // 本链：按链内部门成员担任责任人/负责人
        const chainUserIds = await this.userIdsOfDepts(
          await this.deptIdsOfChain(s.chainCode),
        );
        return {
          OR: [
            { pmUserId: { in: chainUserIds } },
            { engOwnerId: { in: chainUserIds } },
            { salesOwnerId: { in: chainUserIds } },
            { collectorId: { in: chainUserIds } },
          ],
        };
      }
      case 'dept':
      case 'dept_projects': {
        const deptUserIds = await this.userIdsOfDepts(s.deptIdsInSubtree);
        return {
          OR: [
            { pmUserId: { in: deptUserIds } },
            { engOwnerId: { in: deptUserIds } },
            { salesOwnerId: { in: deptUserIds } },
            { collectorId: { in: deptUserIds } },
            { createdBy: s.userId },
          ],
        };
      }
      default: {
        // R-PM 临时授权叠加（FR-M00-003：所负责项目全量）
        return s.grantedProjectIds.length
          ? { OR: [selfCond, { id: { in: s.grantedProjectIds } }] }
          : selfCond;
      }
    }
  }

  private async deptIdsOfChain(chainCode: string | null): Promise<bigint[]> {
    if (!chainCode) return [];
    const depts = await this.prisma.sysDept.findMany({
      where: { chainCode, deletedAt: null },
      select: { id: true },
    });
    return depts.map((d) => d.id);
  }

  private async userIdsOfDepts(deptIds: bigint[]): Promise<bigint[]> {
    if (!deptIds.length) return [];
    const users = await this.prisma.sysUser.findMany({
      where: { deptId: { in: deptIds }, deletedAt: null, status: 'active' },
      select: { id: true },
    });
    return users.map((u) => u.id);
  }
}
