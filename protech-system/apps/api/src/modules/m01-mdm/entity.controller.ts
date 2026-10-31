import { Body, Controller, Get } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { RequirePermission } from '../../shared/guards';

/** T-10 法人主体（两司分账全局维度 E-15；Q-01：TGL 停用统一 TLD） */
@Controller('mdm/entities')
export class EntityController {
  constructor(private readonly prisma: PrismaService) {}

  @Get()
  @RequirePermission('mdm:entity:list')
  async list() {
    const rows = await this.prisma.legalEntity.findMany({
      where: { deletedAt: null },
      orderBy: { entityCode: 'asc' },
    });
    return rows.map((e) => ({ ...e, id: e.id.toString() }));
  }
}
