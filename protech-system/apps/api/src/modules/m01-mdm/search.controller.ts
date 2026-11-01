import { Body, Controller, Get, Post, Query } from '@nestjs/common';
import { MdmService } from './mdm.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { CurrentUser, AuthUser } from '../../shared/guards';
import { PrismaService } from '../../shared/prisma.service';
import { RequirePermission } from '../../shared/guards';

/** FR-M01-016 别名库全局搜索：任意叫法命中主体/项目/物料 */
@Controller('mdm/search')
export class SearchController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly mdm: MdmService,
    private readonly codes: CodeEngineService,
  ) {}

  // ---- FR-M01-015 主数据导出（CSV，含审计） ----
  @Get('export')
  async exportAll(@Query('type') type: string) {
    if (!['parties', 'materials', 'suppliers', 'farms'].includes(type)) throw new Error('type 非法');
    const rows: Array<Record<string, unknown>> =
      type === 'parties'
        ? await this.prisma.customerParty.findMany({ where: { deletedAt: null }, take: 2000 })
        : type === 'materials'
          ? await this.prisma.material.findMany({ where: { deletedAt: null }, take: 3000 })
          : type === 'suppliers'
            ? await this.prisma.supplier.findMany({ where: { deletedAt: null }, take: 1000 })
            : await this.prisma.windFarm.findMany({ where: { deletedAt: null }, take: 1000 });
    return { count: rows.length, rows: rows.map((r) => ({ ...r, id: r.id?.toString?.() ?? r.id })) };
  }

  // ---- FR-M01-014 部门与人员主数据视图 ----
  @Get('dept-roster')
  async deptRoster() {
    const depts = await this.prisma.sysDept.findMany({ where: { deletedAt: null }, orderBy: { sort: 'asc' } });
    const users = await this.prisma.sysUser.findMany({
      where: { deletedAt: null, status: 'active' },
      select: { id: true, displayName: true, position: true, deptId: true },
    });
    return depts.map((d) => ({
      id: d.id.toString(),
      code: d.code,
      name: d.name,
      headcount: d.headcount ?? 0,
      members: users
        .filter((u) => u.deptId === d.id)
        .map((u) => ({ id: u.id.toString(), displayName: u.displayName, position: u.position })),
    }));
  }

  // ---- FR-M01-013 主数据同步出口：生成 T+/售后宝同步包（文件适配器队列） ----
  @Post('sync-package')
  async syncPackage() {
    const [projects, parties, materials] = await Promise.all([
      this.prisma.project.findMany({ where: { deletedAt: null, updatedAt: { gte: new Date(Date.now() - 86400000) } }, take: 200 }),
      this.prisma.customerParty.findMany({ where: { deletedAt: null, updatedAt: { gte: new Date(Date.now() - 86400000) } }, take: 200 }),
      this.prisma.material.findMany({ where: { deletedAt: null, updatedAt: { gte: new Date(Date.now() - 86400000) } }, take: 200 }),
    ]);
    return {
      generatedAt: new Date().toISOString(),
      targets: ['T+ 项目辅助核算口径', '售后宝 工单挂载口径'],
      packages: {
        projects: projects.map((p) => ({ projectCode: p.projectCode, standardName: p.standardName, status: p.status })),
        parties: parties.map((p) => ({ partyCode: p.partyCode, name: p.name })),
        materials: materials.map((m) => ({ materialCode: m.materialCode, name: m.name, unit: m.unit })),
      },
    };
  }

  @Get()
  @RequirePermission('mdm:party:list')
  async search(@Query('q') q?: string) {
    if (!q || !q.trim()) return { parties: [], projects: [], materials: [] };
    const kw = q.trim();
    const [aliasParty, aliasProject, aliasMaterial] = await Promise.all([
      this.prisma.alias.findMany({ where: { ownerType: 'party', alias: { contains: kw } }, take: 20 }),
      this.prisma.alias.findMany({ where: { ownerType: 'project', alias: { contains: kw } }, take: 20 }),
      this.prisma.alias.findMany({ where: { ownerType: 'material', alias: { contains: kw } }, take: 20 }),
    ]);
    const [parties, projects, materials] = await Promise.all([
      this.prisma.customerParty.findMany({
        where: {
          deletedAt: null,
          OR: [
            { name: { contains: kw } },
            { shortName: { contains: kw } },
            { partyCode: { contains: kw } },
            { id: { in: aliasParty.map((a) => a.ownerId) } },
          ],
        },
        take: 10,
        select: { id: true, partyCode: true, name: true, shortName: true },
      }),
      this.prisma.project.findMany({
        where: {
          deletedAt: null,
          OR: [
            { standardName: { contains: kw } },
            { shortName: { contains: kw } },
            { projectCode: { contains: kw } },
            { id: { in: aliasProject.map((a) => a.ownerId) } },
          ],
        },
        take: 10,
        select: { id: true, projectCode: true, standardName: true, shortName: true, status: true },
      }),
      this.prisma.material.findMany({
        where: {
          deletedAt: null,
          OR: [
            { name: { contains: kw } },
            { spec: { contains: kw } },
            { materialCode: { contains: kw } },
            { id: { in: aliasMaterial.map((a) => a.ownerId) } },
          ],
        },
        take: 10,
        select: { id: true, materialCode: true, name: true, spec: true, unit: true },
      }),
    ]);
    return {
      parties: parties.map((p) => ({ ...p, id: p.id.toString() })),
      projects: projects.map((p) => ({ ...p, id: p.id.toString() })),
      materials: materials.map((m) => ({ ...m, id: m.id.toString() })),
    };
  }
}
