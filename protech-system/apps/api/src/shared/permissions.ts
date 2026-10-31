/**
 * 权限码注册表（FR-M00-002：菜单/按钮/数据三级授权）。
 * 种子数据、守卫校验、前端菜单三处同源；新增模块在此登记。
 */
export interface PermDef {
  code: string;
  name: string;
  type: 'menu' | 'button' | 'api';
  module: string; // M00 ~ M16
}

export const PERMISSIONS: PermDef[] = [
  // ---- M00 平台底座 ----
  { code: 'sys:user:list', name: '用户查询', type: 'menu', module: 'M00' },
  { code: 'sys:user:create', name: '新增用户', type: 'button', module: 'M00' },
  { code: 'sys:user:update', name: '编辑用户/停用', type: 'button', module: 'M00' },
  { code: 'sys:user:assign-role', name: '分配角色', type: 'button', module: 'M00' },
  { code: 'sys:dept:manage', name: '部门管理', type: 'button', module: 'M00' },
  { code: 'sys:role:manage', name: '角色与权限管理', type: 'button', module: 'M00' },
  { code: 'sys:config:manage', name: '系统配置管理', type: 'button', module: 'M00' },
  { code: 'sys:enum:manage', name: '数据字典管理', type: 'button', module: 'M00' },
  { code: 'sys:code:manage', name: '编码规则管理', type: 'button', module: 'M00' },
  { code: 'sys:pmgrant:manage', name: '项目级临时授权', type: 'button', module: 'M00' },
  { code: 'sys:audit:list', name: '审计日志查询', type: 'menu', module: 'M00' },
  { code: 'sys:monitor:view', name: '系统监控', type: 'menu', module: 'M00' },
  { code: 'sys:file:upload', name: '附件上传', type: 'button', module: 'M00' },
  // ---- M01 主数据 ----
  { code: 'mdm:entity:list', name: '法人主体查询', type: 'menu', module: 'M01' },
  { code: 'mdm:group:list', name: '客户集团查询', type: 'menu', module: 'M01' },
  { code: 'mdm:group:manage', name: '客户集团维护', type: 'button', module: 'M01' },
  { code: 'mdm:party:list', name: '客户主体查询', type: 'menu', module: 'M01' },
  { code: 'mdm:party:create', name: '新建客户主体', type: 'button', module: 'M01' },
  { code: 'mdm:party:update', name: '编辑客户主体', type: 'button', module: 'M01' },
  { code: 'mdm:alias:add', name: '别名维护', type: 'button', module: 'M01' },
  { code: 'mdm:farm:list', name: '风场库查询', type: 'menu', module: 'M01' },
  { code: 'mdm:farm:create', name: '新建风场', type: 'button', module: 'M01' },
  { code: 'mdm:farm:update', name: '编辑风场', type: 'button', module: 'M01' },
  { code: 'mdm:material:list', name: '物料查询', type: 'menu', module: 'M01' },
  { code: 'mdm:material:create', name: '新增物料', type: 'button', module: 'M01' },
  { code: 'mdm:material:update', name: '编辑物料', type: 'button', module: 'M01' },
  { code: 'mdm:supplier:list', name: '供应商查询', type: 'menu', module: 'M01' },
  { code: 'mdm:supplier:manage', name: '供应商维护', type: 'button', module: 'M01' },
  { code: 'mdm:project:list', name: '项目档案查询', type: 'menu', module: 'M01' },
  { code: 'mdm:project:create', name: '项目建档', type: 'button', module: 'M01' },
  { code: 'mdm:project:update', name: '项目编辑（三类变更）', type: 'button', module: 'M01' },
  { code: 'mdm:project:item', name: '产品子项维护', type: 'button', module: 'M01' },
  { code: 'mdm:change:submit', name: '主数据变更提交', type: 'button', module: 'M01' },
  { code: 'mdm:change:review', name: '主数据变更审批', type: 'button', module: 'M01' },
  { code: 'mdm:export', name: '主数据导出', type: 'button', module: 'M01' },
];

/** 各权限默认授予的角色（FR-M00-002 九角色矩阵；R-SYS 恒为全量 '*'） */
export const PERM_ROLES: Record<string, string[]> = {
  'sys:user:list': ['R-EXEC', 'R-GA', 'R-SYS'],
  'sys:user:create': ['R-GA', 'R-SYS'],
  'sys:user:update': ['R-GA', 'R-SYS'],
  'sys:user:assign-role': ['R-SYS'],
  'sys:dept:manage': ['R-GA', 'R-SYS'],
  'sys:role:manage': ['R-SYS'],
  'sys:config:manage': ['R-SYS'],
  'sys:enum:manage': ['R-GA', 'R-SYS'],
  'sys:code:manage': ['R-SYS'],
  'sys:pmgrant:manage': ['R-SYS'],
  'sys:audit:list': ['R-EXEC', 'R-GA', 'R-SYS'],
  'sys:monitor:view': ['R-SYS'],
  'sys:file:upload': ['R-EXEC', 'R-CHAIN', 'R-DEPT', 'R-PM', 'R-OP', 'R-FIN', 'R-QA', 'R-GA', 'R-SYS'],
  'mdm:entity:list': ['R-EXEC', 'R-CHAIN', 'R-DEPT', 'R-PM', 'R-OP', 'R-FIN', 'R-QA', 'R-GA', 'R-SYS'],
  'mdm:group:list': ['R-EXEC', 'R-CHAIN', 'R-DEPT', 'R-PM', 'R-OP', 'R-FIN', 'R-GA', 'R-SYS'],
  'mdm:group:manage': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:party:list': ['R-EXEC', 'R-CHAIN', 'R-DEPT', 'R-PM', 'R-OP', 'R-FIN', 'R-GA', 'R-SYS'],
  'mdm:party:create': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:party:update': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:alias:add': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:farm:list': ['R-EXEC', 'R-CHAIN', 'R-DEPT', 'R-PM', 'R-OP', 'R-FIN', 'R-QA', 'R-GA', 'R-SYS'],
  'mdm:farm:create': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:farm:update': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:material:list': ['R-EXEC', 'R-CHAIN', 'R-DEPT', 'R-PM', 'R-OP', 'R-FIN', 'R-QA', 'R-GA', 'R-SYS'],
  'mdm:material:create': ['R-GA', 'R-SYS'],
  'mdm:material:update': ['R-GA', 'R-SYS'],
  'mdm:supplier:list': ['R-EXEC', 'R-CHAIN', 'R-DEPT', 'R-PM', 'R-OP', 'R-FIN', 'R-GA', 'R-SYS'],
  'mdm:supplier:manage': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:project:list': ['R-EXEC', 'R-CHAIN', 'R-DEPT', 'R-PM', 'R-OP', 'R-FIN', 'R-QA', 'R-GA', 'R-SYS'],
  'mdm:project:create': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:project:update': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:project:item': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:change:submit': ['R-CHAIN', 'R-GA', 'R-SYS'],
  'mdm:change:review': ['R-CHAIN', 'R-SYS'],
  'mdm:export': ['R-EXEC', 'R-CHAIN', 'R-GA', 'R-SYS'],
};
