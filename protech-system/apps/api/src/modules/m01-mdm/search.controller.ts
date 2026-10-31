import { Controller, Get, Query } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { RequirePermission } from '../../shared/guards';

/** FR-M01-016 别名库全局搜索：任意叫法命中主体/项目/物料 */
@Controller('mdm/search')
export class SearchController {
  constructor(private readonly prisma: PrismaService) {}

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
