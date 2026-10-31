/**
 * 种子数据（dev/test；prod 首次部署同样执行一次）。
 * 来源：01 册 §三（14 部门 121 人实测）/ §2.3（142→14 集团）/ 05 册 §四（编码规则）/ FR-M00-009（配置默认值）。
 * 幂等策略：先清后插（dev/test）；生产使用 prisma migrate + 只在空库执行。
 */
import { PrismaClient } from '@prisma/client';
import * as bcrypt from 'bcryptjs';
import { CODE_RULES, CONFIG_DEFAULTS_SEED } from './seed-data';

const prisma = new PrismaClient();

const DEMO_PASSWORD = 'ProjLife@2026';

// ---------- 01 册 §3.1 组织结构（14 部门 + 技术中心两组 = 16 行，共 121 人） ----------
interface DeptDef {
  code: string;
  name: string;
  n: number;
  chain?: string;
  board?: string;
  children?: DeptDef[];
  leaderRole?: string;
  rolePlaceholder?: string;
}

const DEPTS: DeptDef[] = [
  { code: 'ZJCB', name: '总经理部', n: 3, board: '①', rolePlaceholder: '高管' },
  { code: 'SWB', name: '商务部', n: 5, chain: 'order', board: '②', rolePlaceholder: '商务跟单' },
  { code: 'JHB', name: '计划部', n: 4, chain: 'supply', board: '③', rolePlaceholder: '计划员' },
  { code: 'CGB', name: '采购部', n: 4, chain: 'supply', board: '③', rolePlaceholder: '采购员' },
  { code: 'CCB', name: '仓储部', n: 4, chain: 'supply', board: '③', rolePlaceholder: '仓管员' },
  { code: 'SCB', name: '生产部', n: 20, chain: 'mfg', board: '④', rolePlaceholder: '装配工' },
  { code: 'GYB', name: '工艺部', n: 6, chain: 'mfg', board: '④', rolePlaceholder: '工艺员' },
  { code: 'ZLAQ', name: '质量安全部', n: 7, chain: 'mfg', board: '④', rolePlaceholder: '质检员' },
  { code: 'GCJS', name: '工程技术部', n: 23, chain: 'delivery', board: '⑤', rolePlaceholder: '项目经理' },
  {
    code: 'JSZX',
    name: '技术中心',
    n: 0,
    children: [
      { code: 'JCXZ', name: '技术中心·系统集成组', n: 17, chain: 'delivery', board: '⑤', rolePlaceholder: '集成工程师' },
      { code: 'YFSJ', name: '技术中心·研发设计组', n: 10, chain: 'rd', board: '⑥', rolePlaceholder: '设计工程师' },
    ],
  },
  { code: 'XSTD', name: '销售团队', n: 3, chain: 'market', board: '⑦', rolePlaceholder: '销售' },
  { code: 'SCB_SH', name: '市场部', n: 2, chain: 'market', board: '⑦', rolePlaceholder: '市场专员' },
  { code: 'CWB', name: '财务部', n: 6, chain: 'fin', board: '⑧', rolePlaceholder: '财务' },
  { code: 'QGB', name: '企管部', n: 7, chain: 'ga', board: '⑨', rolePlaceholder: '企管专员' },
];

// ---------- 03 册 §3.2 九角色 ----------
const ROLES: Array<{ code: string; name: string; maxDataScope: string }> = [
  { code: 'R-EXEC', name: '总经理/高管', maxDataScope: 'all' },
  { code: 'R-CHAIN', name: '链主', maxDataScope: 'chain' },
  { code: 'R-DEPT', name: '部门负责人', maxDataScope: 'dept_projects' },
  { code: 'R-PM', name: '项目负责人', maxDataScope: 'self' },
  { code: 'R-OP', name: '业务操作员', maxDataScope: 'self' },
  { code: 'R-FIN', name: '财务', maxDataScope: 'all' },
  { code: 'R-QA', name: '质量安全', maxDataScope: 'dept' },
  { code: 'R-GA', name: '企管/档案', maxDataScope: 'all' },
  { code: 'R-SYS', name: '系统管理员', maxDataScope: 'all' },
];

// 演示账号（测试/演示矩阵；业务操作员种子另含 121 人实名占位账号）
const DEMO_USERS: Array<{ username: string; displayName: string; dept: string; roles: string[]; position: string }> = [
  { username: 'admin', displayName: '系统管理员', dept: 'QGB', roles: ['R-SYS'], position: 'IT 兼职' },
  { username: 'exec', displayName: '总经理', dept: 'ZJCB', roles: ['R-EXEC'], position: '总经理' },
  { username: 'chain-sw', displayName: '商务部负责人', dept: 'SWB', roles: ['R-CHAIN', 'R-DEPT'], position: '部门负责人' },
  { username: 'chain-pmc', displayName: '计划部负责人', dept: 'JHB', roles: ['R-CHAIN', 'R-DEPT'], position: '部门负责人' },
  { username: 'chain-mfg', displayName: '生产部负责人', dept: 'SCB', roles: ['R-CHAIN', 'R-DEPT'], position: '部门负责人' },
  { username: 'chain-eng', displayName: '工程技术部负责人', dept: 'GCJS', roles: ['R-CHAIN', 'R-DEPT'], position: '部门负责人' },
  { username: 'dept-cg', displayName: '采购部负责人', dept: 'CGB', roles: ['R-DEPT'], position: '部门负责人' },
  { username: 'pm-demo', displayName: '项目负责人（演示）', dept: 'GCJS', roles: ['R-PM'], position: '项目经理' },
  { username: 'op-demo', displayName: '仓管员（演示）', dept: 'CCB', roles: ['R-OP'], position: '仓管员' },
  { username: 'fin-demo', displayName: '财务（演示）', dept: 'CWB', roles: ['R-FIN'], position: '财务主管' },
  { username: 'qa-demo', displayName: '质检员（演示）', dept: 'ZLAQ', roles: ['R-QA'], position: '质检员' },
  { username: 'ga-demo', displayName: '企管专员（演示）', dept: 'QGB', roles: ['R-GA'], position: '企管专员' },
];

// ---------- 01 册 §2.3 客户集团种子（142→14 归并底稿的 14 个集团/类别） ----------
const GROUPS = [
  '三峡新能源',
  '中国华能',
  '中广核',
  '国华（国电投）',
  '华电',
  '远景能源',
  '三一重能',
  '东方电气',
  '海装风电',
  '联合动力',
  '明阳智能',
  '运达股份',
  '中车风电',
  '金风科技',
];

async function main() {
  console.log('[seed] 开始…');
  const hash = bcrypt.hashSync(DEMO_PASSWORD, 10);

  // 清库（dev/test 幂等重建）
  const tables = [
    'alias', 'norm_dict', 'md_code_map', 'md_change_request', 'project_product_item', 'project',
    'supplier', 'material', 'wind_farm', 'customer_party', 'customer_group', 'legal_entity',
    'sys_file', 'sys_enum_item', 'sys_code_rule', 'sys_config', 'sys_notify', 'sys_audit_log',
    'sys_session', 'project_pm_grant', 'sys_role_permission', 'sys_user_role', 'sys_permission',
    'sys_role', 'sys_user', 'sys_dept',
  ];
  for (const t of tables) {
    await prisma.$executeRawUnsafe(`DELETE FROM "${t}"`).catch(() => undefined);
  }

  // 法人主体（T-10）
  for (const e of [
    { entityCode: 'PRT', name: '普若泰克科技发展（北京）有限公司', contractPrefix: 'PRT' },
    { entityCode: 'TLD', name: '河北特固兰新能源科技有限公司', contractPrefix: 'TLD' },
  ]) {
    await prisma.legalEntity.create({ data: e });
  }

  // 部门树
  const deptIds = new Map<string, bigint>();
  let sort = 0;
  for (const d of DEPTS) {
    const row = await prisma.sysDept.create({
      data: { code: d.code, name: d.name, path: '/', chainCode: d.chain ?? null, boardCode: d.board ?? null, headcount: d.n, sort: sort++ },
    });
    deptIds.set(d.code, row.id);
    if (d.children) {
      for (const c of d.children) {
        const child = await prisma.sysDept.create({
          data: {
            code: c.code,
            name: c.name,
            parentId: row.id,
            path: `/${row.id}/`,
            chainCode: c.chain ?? null,
            boardCode: c.board ?? null,
            headcount: c.n,
            sort: sort++,
          },
        });
        deptIds.set(c.code, child.id);
      }
    }
  }

  // 角色 + 权限
  const permRows = await Promise.all(
    (await import('./src/shared/permissions')).PERMISSIONS.map((p) =>
      prisma.sysPermission.create({ data: { code: p.code, name: p.name, type: p.type, module: p.module } }),
    ),
  );
  const permIdByCode = new Map(permRows.map((p) => [p.code, p.id]));
  const { PERM_ROLES } = await import('./src/shared/permissions');
  const roleIds = new Map<string, bigint>();
  for (const r of ROLES) {
    const row = await prisma.sysRole.create({ data: r });
    roleIds.set(r.code, row.id);
  }
  for (const [code, roleCodes] of Object.entries(PERM_ROLES)) {
    const pid = permIdByCode.get(code);
    if (!pid) continue;
    for (const rc of roleCodes) {
      if (rc === 'R-SYS') continue; // R-SYS 随后全量授予
      await prisma.sysRolePermission.create({ data: { roleId: roleIds.get(rc)!, permissionId: pid } });
    }
  }
  // R-SYS 全量
  for (const p of permRows) {
    await prisma.sysRolePermission.create({ data: { roleId: roleIds.get('R-SYS')!, permissionId: p.id } });
  }

  // 121 人 + 演示账号
  let headcount = 0;
  const deptUsers = new Map<string, bigint>(); // 部门 code → 部门负责人 userId（供后续切片引用）
  void deptUsers;
  const userRows: Array<{ id: bigint; username: string }> = [];
  for (const d of DEPTS) {
    if (d.n > 0) {
      const first = await prisma.sysUser.create({
        data: {
          username: `${d.code}01`,
          displayName: `${d.name}负责人`,
          passwordHash: hash,
          deptId: deptIds.get(d.code)!,
          position: '部门负责人',
        },
      });
      headcount++;
      deptUsers.set(d.code, first.id);
      userRows.push({ id: first.id, username: first.username });
      await prisma.sysUserRole.create({
        data: { userId: first.id, roleId: roleIds.get(d.code === 'ZJCB' ? 'R-EXEC' : 'R-DEPT')! },
      });
      for (let i = 2; i <= d.n; i++) {
        const u = await prisma.sysUser.create({
          data: {
            username: `${d.code}${String(i).padStart(2, '0')}`,
            displayName: `${d.name}${d.rolePlaceholder ?? '专员'}${String(i).padStart(2, '0')}`,
            passwordHash: hash,
            deptId: deptIds.get(d.code)!,
            position: d.rolePlaceholder ?? '专员',
          },
        });
        headcount++;
        userRows.push({ id: u.id, username: u.username });
        await prisma.sysUserRole.create({ data: { userId: u.id, roleId: roleIds.get('R-OP')! } });
      }
    }
    for (const c of d.children ?? []) {
      const first = await prisma.sysUser.create({
        data: {
          username: `${c.code}01`,
          displayName: `${c.name}组长`,
          passwordHash: hash,
          deptId: deptIds.get(c.code)!,
          position: '组长',
        },
      });
      headcount++;
      deptUsers.set(c.code, first.id);
      userRows.push({ id: first.id, username: first.username });
      await prisma.sysUserRole.create({ data: { userId: first.id, roleId: roleIds.get('R-DEPT')! } });
      for (let i = 2; i <= c.n; i++) {
        const u = await prisma.sysUser.create({
          data: {
            username: `${c.code}${String(i).padStart(2, '0')}`,
            displayName: `${c.name}${c.rolePlaceholder ?? '专员'}${String(i).padStart(2, '0')}`,
            passwordHash: hash,
            deptId: deptIds.get(c.code)!,
            position: c.rolePlaceholder ?? '专员',
          },
        });
        headcount++;
        userRows.push({ id: u.id, username: u.username });
        await prisma.sysUserRole.create({ data: { userId: u.id, roleId: roleIds.get('R-OP')! } });
      }
    }
  }
  for (const du of DEMO_USERS) {
    const u = await prisma.sysUser.create({
      data: {
        username: du.username,
        displayName: du.displayName,
        passwordHash: hash,
        deptId: deptIds.get(du.dept)!,
        position: du.position,
      },
    });
    userRows.push({ id: u.id, username: u.username });
    for (const r of du.roles) {
      await prisma.sysUserRole.create({ data: { userId: u.id, roleId: roleIds.get(r)! } });
    }
  }
  console.log(`[seed] 部门 ${deptIds.size}、用户 ${headcount + DEMO_USERS.length}（含 121 实测 + 演示矩阵）`);

  // 编码规则 + 配置 + 枚举
  for (const r of CODE_RULES) {
    await prisma.sysCodeRule.create({
      data: { bizType: r.bizType, pattern: r.pattern, seqLen: r.seqLen, yearly: r.yearly, currentPeriod: r.yearly ? String(new Date().getFullYear()) : 'ALL', currentSeq: 0, remark: r.remark ?? null },
    });
  }
  for (const [key, cfg] of Object.entries(CONFIG_DEFAULTS_SEED)) {
    await prisma.sysConfig.create({ data: { key, value: cfg.value, valueType: cfg.type, remark: cfg.remark } });
  }
  const enums = await import('@protech/shared-types');
  const enumDefs: Array<[string, Record<string, string>]> = [
    ['PARTY_TYPE', enums.PARTY_TYPE],
    ['ORDER_TYPE', enums.ORDER_TYPE],
    ['PRODUCT_LINE', enums.PRODUCT_LINE],
    ['PROJECT_STATUS', enums.PROJECT_STATUS],
    ['CONTRACT_STATUS', enums.CONTRACT_STATUS],
    ['CONTRACT_NATURE', enums.CONTRACT_NATURE],
    ['CARD_STATUS', enums.CARD_STATUS],
    ['REPLENISH_REASON', enums.REPLENISH_REASON],
    ['VARIANCE_CAUSE', enums.VARIANCE_CAUSE],
    ['WORK_ORDER_STATUS', enums.WORK_ORDER_STATUS],
    ['PO_STATUS', enums.PO_STATUS],
    ['DISPOSAL_WAY', enums.DISPOSAL_WAY],
    ['ARCHIVE_CATEGORY', enums.ARCHIVE_CATEGORY],
    ['RETENTION', enums.RETENTION],
    ['LEGAL_ENTITY', enums.LEGAL_ENTITY],
    ['DATA_SCOPE', enums.DATA_SCOPE],
    ['ESCALATION_LEVEL', enums.ESCALATION_LEVEL],
    ['AI_LEVEL', enums.AI_LEVEL],
    ['BOM_TYPE', enums.BOM_TYPE],
    ['THREE_COLOR', enums.THREE_COLOR],
  ];
  let enumCount = 0;
  for (const [type, def] of enumDefs) {
    let i = 0;
    for (const [code, label] of Object.entries(def)) {
      await prisma.sysEnumItem.create({ data: { enumType: type, code, label, sort: i++ } });
      enumCount++;
    }
  }
  console.log(`[seed] 编码规则 ${CODE_RULES.length}、配置 ${Object.keys(CONFIG_DEFAULTS_SEED).length}、枚举 ${enumCount}`);

  // 主数据样例（142→14 集团 + 代表主体/别名/风场/物料/归一词典）
  const entityPRT = await prisma.legalEntity.findFirst({ where: { entityCode: 'PRT' } });
  const groupIds = new Map<string, bigint>();
  for (let i = 0; i < GROUPS.length; i++) {
    const g = await prisma.customerGroup.create({
      data: { groupCode: `G${String(i + 1).padStart(3, '0')}`, name: GROUPS[i] },
    });
    groupIds.set(GROUPS[i], g.id);
  }
  const partySeeds = [
    { group: '远景能源', name: '远景能源科技集团有限公司', shortName: '远景能源', types: ['oem'], aliases: ['远景', '远景能源集团', 'Envision'] },
    { group: '三峡新能源', name: '三峡新能源（集团）有限公司', shortName: '三峡新能源', types: ['owner'], aliases: ['三峡集团', '三峡新能源集团'] },
    { group: '中国华能', name: '华能新能源股份有限公司', shortName: '华能新能源', types: ['owner'], aliases: ['华能'] },
    { group: '金风科技', name: '金风科技集团股份有限公司', shortName: '金风科技', types: ['oem'], aliases: ['金风'] },
  ];
  let partySeq = 86; // 工具表 C0087 起
  for (const s of partySeeds) {
    const p = await prisma.customerParty.create({
      data: {
        partyCode: `C${String(partySeq++).padStart(4, '0')}`,
        groupId: groupIds.get(s.group),
        name: s.name,
        shortName: s.shortName,
        types: JSON.stringify(s.types),
      },
    });
    for (const a of s.aliases) {
      await prisma.alias.create({ data: { ownerType: 'party', ownerId: p.id, alias: a, source: 'import' } });
    }
  }
  const farm = await prisma.windFarm.create({
    data: { farmCode: 'WF2026-001', fullName: '姚安大龙口风力发电场', province: '云南省', city: '楚雄州', county: '姚安县', capacityMw: 120 },
  });
  for (const m of [
    { materialCode: '3080-FD-0001', name: '消防电池', spec: '12V 7Ah', unit: '只', category: '电气件', abcClass: 'A' },
    { materialCode: '3080-TS-0002', name: '钢丝绳', spec: 'φ8mm 30m', unit: '根', category: '标准件', abcClass: 'B' },
    { materialCode: '3084-XC-0003', name: '小车按钮', spec: '急停带灯', unit: '个', category: '电气件', abcClass: 'A' },
  ]) {
    await prisma.material.create({ data: m });
  }
  for (const n of [
    { type: 'typo', raw: '罗杆', normalized: '螺杆' },
    { type: 'typo', raw: '菠纹管', normalized: '波纹管' },
    { type: 'symbol', raw: '（', normalized: '(' },
    { type: 'symbol', raw: '）', normalized: ')' },
    { type: 'unit', raw: '台套', normalized: '台' },
    { type: 'sep', raw: '－', normalized: '-' },
  ]) {
    await prisma.normDict.create({ data: n });
  }
  void entityPRT;

  // 种子已占用编码 → 同步编码规则流水（BR-09 不可复用）
  await prisma.sysCodeRule.updateMany({
    where: { bizType: 'wind_farm' },
    data: { currentPeriod: String(new Date().getFullYear()), currentSeq: 1 },
  });
  await prisma.sysCodeRule.updateMany({
    where: { bizType: 'party' },
    data: { currentPeriod: 'ALL', currentSeq: partySeq - 1 },
  });

  console.log('[seed] 完成 ✓');
  console.log(`[seed] 演示账号密码统一为 ${DEMO_PASSWORD}（admin/exec/chain-sw/chain-pmc/chain-mfg/chain-eng/pm-demo/op-demo/fin-demo/qa-demo/ga-demo）`);
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
