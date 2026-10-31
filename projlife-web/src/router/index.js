/**
 * 路由总表（UID-05 §二，131 条口径；本批落"完整"档中已实现页 + 旧路径兼容重定向）。
 * 命名唯一来源 UID-05：/{域}/{资源}；禁止组件内硬编码路径字符串（统一 router.push({name})）。
 * 菜单按权限动态渲染（FR-SYS-03）；meta.permCode 为侧栏高亮键。
 */
import Vue from 'vue'
import VueRouter from 'vue-router'
import store from '@/store'

Vue.use(VueRouter)

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue') },
  { path: '/auth/change-password', name: 'changePassword', component: () => import('@/views/auth/ChangePassword.vue'), meta: { title: '修改密码' } },
  { path: '/403', name: 'forbidden', component: () => import('@/views/error/Forbidden.vue'), meta: { title: '无权访问' } },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    children: [
      // ① 工作台
      { path: '', redirect: '/workbench' },
      { path: 'workbench', name: 'workbench', component: () => import('@/views/Workbench.vue'), meta: { title: '个人工作台', permCode: 'menu:workbench' } },
      { path: 'workbench/message', name: 'workbenchMessage', component: () => import('@/views/Notifications.vue'), meta: { title: '消息中心', permCode: 'menu:workbench' } },
      // ② 合同与项目
      { path: 'crm/opportunity', name: 'crmOpportunity', component: () => import('@/views/crm/Opportunities.vue'), meta: { title: '商机台账', permCode: 'menu:crm' } },
      { path: 'crm/quotation', name: 'crmQuotation', component: () => import('@/views/crm/Quotations.vue'), meta: { title: '报价单台账', permCode: 'menu:crm' } },
      { path: 'ct/contract', name: 'ctContract', component: () => import('@/views/contract/Contracts.vue'), meta: { title: '合同台账', permCode: 'menu:contract' } },
      { path: 'ct/contract/:id', name: 'ctContractDetail', component: () => import('@/views/contract/ContractDetail.vue'), meta: { title: '合同详情', permCode: 'menu:contract' } },
      { path: 'prj/project', name: 'prjProject', component: () => import('@/views/project/Projects.vue'), meta: { title: '项目台账', permCode: 'menu:project' } },
      { path: 'bid/notice', name: 'bidNotice', component: () => import('@/views/bid/BidIntel.vue'), meta: { title: '标讯台账', permCode: 'menu:bid' } },
      // ③ 研发工艺
      { path: 'plm/bom', name: 'plmBom', component: () => import('@/views/plm/Boms.vue'), meta: { title: 'BOM 台账', permCode: 'menu:plm' } },
      // ④ 供应链
      { path: 'inv/txn', name: 'invTxn', component: () => import('@/views/inv/Inventory.vue'), meta: { title: '出入库作业', permCode: 'menu:inv' } },
      // ⑤ 生产质量
      { path: 'mes/order', name: 'mesOrder', component: () => import('@/views/mes/Production.vue'), meta: { title: '生产订单', permCode: 'menu:mes' } },
      { path: 'aps/schedule', name: 'apsSchedule', component: () => import('@/views/mes/Aps.vue'), meta: { title: '排产', permCode: 'menu:mes' } },
      // ⑥ 工程交付
      { path: 'eng/dispatch', name: 'engDispatch', component: () => import('@/views/eng/EngPortal.vue'), meta: { title: '派工单台账', permCode: 'menu:eng' } },
      { path: 'thd/delivery-note', name: 'thdDeliveryNote', component: () => import('@/views/eng/ThdPortal.vue'), meta: { title: '第三方协同', permCode: 'menu:eng' } },
      // ⑦ 成本与看板（九板共用 1 套规格，boardCode 由 kb_board 配置驱动）
      { path: 'kb/board/:boardCode', name: 'kbBoard', component: () => import('@/views/kb/Board.vue'), meta: { title: '经营看板', permCode: 'menu:kb' } },
      { path: 'kb/meeting/:id', name: 'kbMeeting', component: () => import('@/views/kb/MeetingMinutes.vue'), meta: { title: '会议纪要', permCode: 'menu:kb' } },
      { path: 'kb/kanban-legacy', name: 'kbKanbanLegacy', component: () => import('@/views/kb/Kanban.vue'), meta: { title: '卡片台账', permCode: 'menu:kb' } },
      { path: 'cost/report', name: 'costReport', component: () => import('@/views/cost/CostCenter.vue'), meta: { title: '成本报表', permCode: 'menu:cost' } },
      // ⑧ 主数据与治理
      { path: 'mdm/project', name: 'mdmProject', component: () => import('@/views/mdm/MdmConsole.vue'), meta: { title: '项目档案', permCode: 'menu:mdm' } },
      { path: 'arc/archive', name: 'arcArchive', component: () => import('@/views/arc/Archives.vue'), meta: { title: '档案管理', permCode: 'menu:arc' } },
      { path: 'sys/user', name: 'sysUser', component: () => import('@/views/system/Users.vue'), meta: { title: '用户管理', permCode: 'menu:system:user' } },
      { path: 'sys/role', name: 'sysRole', component: () => import('@/views/system/Roles.vue'), meta: { title: '角色权限', permCode: 'menu:system:role' } },
      { path: 'sys/dept', name: 'sysDept', component: () => import('@/views/system/Depts.vue'), meta: { title: '组织部门', permCode: 'menu:system:dept' } },
      { path: 'sys/dict', name: 'sysDict', component: () => import('@/views/system/Dicts.vue'), meta: { title: '数据字典', permCode: 'menu:system:dict' } },
      { path: 'sys/config', name: 'sysConfig', component: () => import('@/views/system/Configs.vue'), meta: { title: '系统参数', permCode: 'menu:system:config' } },
      { path: 'sys/log', name: 'sysLog', component: () => import('@/views/system/Logs.vue'), meta: { title: '审计日志', permCode: 'menu:system:log' } },
      // 模板档占位（UID-05 模板条目：路由/菜单已登记，字段随切片细化——验收 1 无孤儿页面）
      { path: 'workbench/todo', name: 'workbenchTodo', component: () => import('@/views/workbench/Todo.vue'), meta: { title: '待办中心', permCode: 'menu:workbench' } },
      { path: 'workbench/my-task', name: 'workbenchMyTask', component: () => import('@/views/stub/TemplateStub.vue'), meta: { title: '我的任务', permCode: 'menu:workbench', stub: true } },
      { path: 'scm/supplier', name: 'scmSupplier', component: () => import('@/views/scm/Supplier.vue'), meta: { title: '供应商台账', permCode: 'menu:scm' } },
      { path: 'scm/po', name: 'scmPo', component: () => import('@/views/scm/PurchaseOrder.vue'), meta: { title: 'PO 台账', permCode: 'menu:scm' } },
      { path: 'qms/inspection', name: 'qmsInspection', component: () => import('@/views/qms/Inspection.vue'), meta: { title: '检验台账', permCode: 'menu:qms' } },
      { path: 'qms/8d', name: 'qms8d', component: () => import('@/views/qms/EightD.vue'), meta: { title: '8D 报告', permCode: 'menu:qms' } },
      { path: 'qms/spc', name: 'qmsSpc', component: () => import('@/views/qms/SpcChart.vue'), meta: { title: 'SPC 控制图', permCode: 'menu:qms' } },
      { path: 'eam/asset', name: 'eamAsset', component: () => import('@/views/stub/TemplateStub.vue'), meta: { title: '设备台账', permCode: 'menu:eam', stub: true } },
      { path: 'kb/my', name: 'kbMy', component: () => import('@/views/stub/TemplateStub.vue'), meta: { title: '我的看板订阅', permCode: 'menu:kb', stub: true } },
      { path: 'kb/metric', name: 'kbMetric', component: () => import('@/views/kb/MetricDefs.vue'), meta: { title: '指标口径', permCode: 'menu:kb' } },
      { path: 'bid/source', name: 'bidSource', component: () => import('@/views/bid/BidSource.vue'), meta: { title: '数据源', permCode: 'menu:bid' } },
      { path: 'bid/rule', name: 'bidRule', component: () => import('@/views/stub/TemplateStub.vue'), meta: { title: '匹配规则', permCode: 'menu:bid', stub: true } },
      { path: 'sys/code', name: 'sysCode', component: () => import('@/views/stub/TemplateStub.vue'), meta: { title: '编码规则', permCode: 'menu:system:config', stub: true } },
      { path: 'sys/import', name: 'sysImport', component: () => import('@/views/stub/TemplateStub.vue'), meta: { title: '导入批次', permCode: 'menu:system:log', stub: true } },
      // Agent 平台（M19，骨架档）
      { path: 'agt/app', name: 'agtApp', component: () => import('@/views/agent/AgentConsole.vue'), meta: { title: 'Agent 应用注册', permCode: 'menu:agt' } },
      // 个人（仅用户菜单入口，"仅内链"）
      { path: 'profile/appearance', name: 'appearance', component: () => import('@/views/profile/Appearance.vue'), meta: { title: '外观设置' } }
    ]
  },
  // 旧路径兼容重定向（UID-05 路由命名迁移，旧书签不 404）
  { path: '/crm/opportunities', redirect: '/crm/opportunity' },
  { path: '/crm/quotations', redirect: '/crm/quotation' },
  { path: '/contract', redirect: '/ct/contract' },
  { path: '/project', redirect: '/prj/project' },
  { path: '/plm/boms', redirect: '/plm/bom' },
  { path: '/bid/intel', redirect: '/bid/notice' },
  { path: '/inv/center', redirect: '/inv/txn' },
  { path: '/eng/portal', redirect: '/eng/dispatch' },
  { path: '/eng/thd', redirect: '/thd/delivery-note' },
  { path: '/mes/production', redirect: '/mes/order' },
  { path: '/mes/aps', redirect: '/aps/schedule' },
  { path: '/cost/center', redirect: '/cost/report' },
  { path: '/kb/kanban', redirect: '/kb/board/TOTAL' },
  { path: '/arc/archives', redirect: '/arc/archive' },
  { path: '/mdm/console', redirect: '/mdm/project' },
  { path: '/agent/console', redirect: '/agt/app' },
  { path: '/notifications', redirect: '/workbench/message' },
  { path: '*', redirect: '/workbench' }
]

const router = new VueRouter({ routes })

// 登录守卫：公开页之外要求 token（FR-SYS-02）；首登强制改密由 Login 流程跳 /auth/change-password
const PUBLIC_PATHS = ['/login', '/403', '/auth/change-password']
router.beforeEach(async (to, from, next) => {
  if (PUBLIC_PATHS.indexOf(to.path) !== -1) {
    return next()
  }
  if (!store.getters.isLoggedIn) {
    return next({ name: 'login', query: { redirect: to.fullPath } })
  }
  // 首次导航拉取用户信息（含菜单与主题）
  if (!store.state.user) {
    try {
      await store.dispatch('fetchMe')
    } catch (e) {
      return next({ name: 'login' })
    }
  }
  // 菜单级校验：无权限的页面重定向回工作台（服务端权限仍是唯一防线）
  const permCode = to.meta && to.meta.permCode
  if (permCode && !store.state.menus.some(m => findCode(m, permCode))) {
    return next({ name: 'workbench' })
  }
  next()
})

function findCode(node, code) {
  if (node.permCode === code) {
    return true
  }
  return (node.children || []).some(c => findCode(c, code))
}

export default router
