import { Body, Controller, Get, Param, Post, Query } from '@nestjs/common';
import { DeptProjectViewService } from '../m10-board/dept-project-view.service';
import { GovService } from './gov.service';
import { PrismaService } from '../../shared/prisma.service';
import { CodeEngineService } from '../../shared/code-engine.service';
import { AuditService } from '../../shared/audit.service';
import { CurrentUser, AuthUser, RequirePermission } from '../../shared/guards';
import { BizException } from '../../shared/biz.exception';
import { parseId } from '../../shared/serialize';

/** BV-01/BV-03 三级视图接口（FR-M10-025/027） */
@Controller('kb')
export class DeptProjectViewController {
  constructor(private readonly views: DeptProjectViewService) {}

  @Get('dept/:deptId')
  @RequirePermission('kb:view')
  async deptView(@Param('deptId') deptId: string) {
    return this.views.deptView(parseId(deptId));
  }

  @Get('project/:projectId')
  @RequirePermission('kb:view')
  async projectView(@Param('projectId') projectId: string) {
    return this.views.projectView(parseId(projectId));
  }
}

/** M13 数据治理接口（治理权限：R-GA/R-SYS 入口，PERM_ROLES 控制） */
@Controller('gov')
export class GovController {
  constructor(
    private readonly gov: GovService,
    private readonly prisma: PrismaService,
  ) {}

  @Get('tasks')
  @RequirePermission('gov:view')
  async tasks(@Query() q: Record<string, string>) {
    const list = await this.prisma.govTask.findMany({
      where: { deletedAt: null, ...(q.status ? { status: q.status } : {}), ...(q.type ? { type: q.type } : {}) },
      orderBy: { id: 'desc' },
      take: 100,
    });
    return list.map((t) => ({ ...t, id: t.id.toString(), assigneeId: t.assigneeId?.toString() ?? null }));
  }

  @Post('tasks')
  @RequirePermission('gov:manage')
  async createTask(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    const t = await this.gov.createTask(body, me.id);
    return { id: t.id.toString(), taskCode: t.taskCode };
  }

  @Post('tasks/:id/process')
  @RequirePermission('gov:manage')
  async process(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    return this.gov.reviewTask(parseId(id), me.id, 'process', body.result as Record<string, unknown>);
  }

  @Post('tasks/:id/verify')
  @RequirePermission('gov:verify')
  async verify(@Param('id') id: string, @Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    return this.gov.reviewTask(parseId(id), me.id, 'verify', body.result as Record<string, unknown>);
  }

  @Get('dup-workbench')
  @RequirePermission('gov:view')
  async dupWorkbench() {
    return this.gov.dupWorkbench();
  }

  /** 归并（FR-M13-009 仅治理组） */
  @Post('merge-material')
  @RequirePermission('gov:merge')
  async merge(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.masterId || !Array.isArray(body.oldIds)) throw BizException.param('masterId/oldIds 必填');
    return this.gov.mergeMaterial(parseId(body.masterId), (body.oldIds as string[]).map((x) => parseId(x)), me.id);
  }

  @Get('quality-board')
  @RequirePermission('gov:view')
  async qualityBoard() {
    return this.gov.qualityBoard();
  }

  @Post('norm-dict')
  @RequirePermission('gov:manage')
  async addNormDict(@Body() body: Record<string, unknown>, @CurrentUser() me: AuthUser) {
    if (!body.type || !body.raw || !body.normalized) throw BizException.param('type/raw/normalized 必填');
    const row = await this.gov.addNormDict(String(body.type), String(body.raw), String(body.normalized), me.id);
    return { id: row.id.toString() };
  }

  @Get('clean-candidates')
  @RequirePermission('gov:view')
  async cleanCandidates(@Query() q: Record<string, string>) {
    if (!q.target || !q.scope) throw BizException.param('target/scope 必填');
    return this.gov.cleanCandidates(q.target, q.scope as 'party' | 'project' | 'material', Number(q.threshold) || 0.85);
  }
}

/**
 * M15 导入引擎（FR-M15-008）：模板注册制；行级校验；错误回执；全部留审计。
 * S5 以 CSV 文本（首行表头）承载，Excel 解析在 S7 前端/适配器层转换。
 */
interface ImportTemplate {
  key: string;
  required: string[];
  validators: Record<string, (v: string, row: Record<string, string>) => string | null>;
  create: (row: Record<string, string>, userId: bigint) => Promise<unknown>;
}

@Controller('imports')
export class ImportController {
  constructor(
    private readonly prisma: PrismaService,
    private readonly codes: CodeEngineService,
    private readonly audit: AuditService,
  ) {}

  private async templates(): Promise<Record<string, ImportTemplate>> {
    return {
      parties: {
        key: 'parties',
        required: ['名称'],
        validators: {
          类型: (v) => (!v || ['业主', '整机厂', '关联方'].includes(v) ? null : '类型须为 业主/整机厂/关联方'),
        },
        create: async (row, userId) => {
          const typeMap: Record<string, string> = { 业主: 'owner', 整机厂: 'oem', 关联方: 'related' };
          const partyCode = await this.codes.next('party');
          await this.prisma.customerParty.create({
            data: {
              partyCode,
              name: row['名称'],
              shortName: row['简称'] ?? null,
              types: JSON.stringify(row['类型'] ? [typeMap[row['类型']] ?? 'owner'] : ['owner']),
              createdBy: userId,
              updatedBy: userId,
            },
          });
          if (row['别名']) {
            const p = await this.prisma.customerParty.findFirst({ where: { partyCode } });
            if (p) await this.prisma.alias.create({ data: { ownerType: 'party', ownerId: p.id, alias: row['别名'], source: 'import', createdBy: userId } });
          }
          return { partyCode };
        },
      },
      contracts: {
        key: 'contracts',
        required: ['合同名称', '性质', '对方主体编码', '金额_分', '签订日期', '项目编码'],
        validators: {
          性质: (v) => (['XS', 'CG', 'KJ'].includes(v) ? null : '性质须为 XS/CG/KJ'),
          金额_分: (v) => (/^\d+$/.test(v) ? null : '金额必须整数分（BR-10）'),
        },
        create: async (row, userId) => {
          // 存量补录向导（FR-M03-009）：12 关键要素导入，备注挂"待核实"
          const counterparty = await this.prisma.customerParty.findFirst({
            where: { partyCode: row['对方主体编码'], deletedAt: null },
          });
          if (!counterparty) throw new Error(`对方主体不存在：${row['对方主体编码']}`);
          const project = await this.prisma.project.findFirst({
            where: { projectCode: row['项目编码'], deletedAt: null },
          });
          if (!project) throw new Error(`项目不存在：${row['项目编码']}（BR-01 前提）`);
          const entity = await this.prisma.legalEntity.findFirst({ where: { deletedAt: null } });
          if (!entity) throw new Error('法人主体未初始化');
          const contractCode = await this.codes.next('contract', {
            entity: entity.contractPrefix,
            nature: row['性质'],
            biz: row['业务码'] ?? 'SB',
          });
          const contract = await this.prisma.contract.create({
            data: {
              contractCode,
              contractName: row['合同名称'],
              nature: row['性质'],
              bizType: row['业务码'] ?? 'SB',
              ourEntityId: entity.id,
              counterpartyId: counterparty.id,
              amountCents: BigInt(row['金额_分']),
              signedAt: new Date(row['签订日期']),
              warrantyMonths: row['质保月数'] ? Number(row['质保月数']) : null,
              status: 'performing',
              remark: '存量补录（待核实 FR-M03-009）',
              createdBy: userId,
              updatedBy: userId,
            },
          });
          await this.prisma.contractProject.create({
            data: { contractId: contract.id, projectId: project.id, orderLine: '存量补录', createdBy: userId },
          });
          return { contractCode };
        },
      },
      materials: {
        key: 'materials',
        required: ['物料编码', '名称', '单位'],
        validators: {},
        create: async (row, userId) => {
          await this.prisma.material.create({
            data: {
              materialCode: row['物料编码'],
              name: row['名称'],
              spec: row['规格'] ?? null,
              unit: row['单位'],
              category: row['类别'] ?? null,
              abcClass: row['ABC'] ?? null,
              createdBy: userId,
              updatedBy: userId,
            },
          });
          return { code: row['物料编码'] };
        },
      },
    };
  }

  @Post(':templateKey')
  @RequirePermission('import:run')
  async run(@Param('templateKey') templateKey: string, @Body() body: { csv?: string }, @CurrentUser() me: AuthUser) {
    const templates = await this.templates();
    const tpl = templates[templateKey];
    if (!tpl) throw BizException.param(`导入模板不存在：${templateKey}`);
    if (!body.csv || !body.csv.trim()) throw BizException.param('csv 必填（首行表头，逗号分隔）');

    const lines = body.csv.trim().split(/\r?\n/);
    const headers = lines[0].split(',').map((h) => h.trim());
    const errors: Array<{ row: number; field?: string; message: string }> = [];
    let okRows = 0;

    for (let i = 1; i < lines.length; i++) {
      const cells = lines[i].split(',').map((c) => c.trim());
      const row: Record<string, string> = {};
      headers.forEach((h, idx) => (row[h] = cells[idx] ?? ''));
      let rowValid = true;
      for (const req of tpl.required) {
        if (!row[req]) {
          errors.push({ row: i + 1, field: req, message: `必填列缺失：${req}` });
          rowValid = false;
        }
      }
      if (rowValid) {
        for (const [field, validate] of Object.entries(tpl.validators)) {
          const msg = validate(row[field] ?? '', row);
          if (msg) {
            errors.push({ row: i + 1, field, message: msg });
            rowValid = false;
          }
        }
      }
      if (rowValid) {
        try {
          await tpl.create(row, me.id);
          okRows++;
        } catch (e) {
          errors.push({ row: i + 1, message: (e as Error).message.slice(0, 120) });
        }
      }
    }

    const batch = await this.prisma.importBatch.create({
      data: {
        templateKey,
        totalRows: lines.length - 1,
        okRows,
        errorRows: errors.length,
        errors: JSON.stringify(errors),
        status: errors.length === 0 ? 'done' : okRows > 0 ? 'partial' : 'failed',
        createdBy: me.id,
      },
    });
    await this.audit.log({ action: 'import', objectType: 'ImportBatch', objectId: batch.id, after: { templateKey, okRows, errorRows: errors.length } });
    return { batchId: batch.id.toString(), totalRows: batch.totalRows, okRows, errorRows: errors.length, errors: errors.slice(0, 50) };
  }
}
