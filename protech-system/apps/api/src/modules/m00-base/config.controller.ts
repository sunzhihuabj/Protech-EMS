import { Body, Controller, Get, Param, Put } from '@nestjs/common';
import { PrismaService } from '../../shared/prisma.service';
import { AppConfigService } from '../../shared/app-config.service';
import { BizException } from '../../shared/biz.exception';
import { AuditService } from '../../shared/audit.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';

/** FR-M00-009 系统配置中心（阈值参数；改动留痕；默认值=制度值） */
@Controller('sys/configs')
export class ConfigController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly config: AppConfigService,
    private readonly audit: AuditService,
  ) {}

  @Get()
  async list() {
    return (await this.prisma.sysConfig.findMany({ where: { deletedAt: null }, orderBy: { key: 'asc' } })).map(
      (c) => ({ ...c, id: c.id.toString() }),
    );
  }

  @Put(':key')
  @RequirePermission('sys:config:manage')
  async update(@Param('key') key: string, @Body() body: { value?: string; remark?: string }, @CurrentUser() me: AuthUser) {
    if (body.value === undefined) throw BizException.param('value 必填');
    const before = await this.prisma.sysConfig.findFirst({ where: { key, deletedAt: null } });
    if (before) {
      await this.prisma.sysConfig.update({
        where: { id: before.id },
        data: { value: body.value, remark: body.remark ?? before.remark, updatedBy: me.id },
      });
    } else {
      await this.prisma.sysConfig.create({
        data: { key, value: body.value, remark: body.remark ?? null, createdBy: me.id, updatedBy: me.id },
      });
    }
    await this.audit.log({
      action: 'config',
      objectType: 'SysConfig',
      objectId: key,
      before: before?.value,
      after: body.value,
    });
    this.config.invalidate(key);
    return null;
  }
}
