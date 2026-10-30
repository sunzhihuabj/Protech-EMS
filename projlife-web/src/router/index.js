/**
 * 路由（S0-14）：登录 + 主布局；meta.permCode 用于直达 URL 的菜单级校验。
 */
import Vue from 'vue'
import VueRouter from 'vue-router'
import store from '@/store'

Vue.use(VueRouter)

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue') },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    children: [
      { path: '', name: 'workbench', component: () => import('@/views/Workbench.vue'), meta: { title: '个人工作台', permCode: 'menu:workbench' } },
      { path: 'notifications', name: 'notifications', component: () => import('@/views/Notifications.vue'), meta: { title: '消息中心', permCode: 'menu:notification' } },
      { path: 'crm/opportunities', name: 'opportunities', component: () => import('@/views/crm/Opportunities.vue'), meta: { title: '商机管理', permCode: 'menu:crm' } },
      { path: 'contract', name: 'contracts', component: () => import('@/views/contract/Contracts.vue'), meta: { title: '合同管理', permCode: 'menu:contract' } },
      { path: 'project', name: 'projects', component: () => import('@/views/project/Projects.vue'), meta: { title: '项目管理', permCode: 'menu:project' } },
      { path: 'plm/boms', name: 'boms', component: () => import('@/views/plm/Boms.vue'), meta: { title: '研发与BOM', permCode: 'menu:plm' } },
      { path: 'crm/quotations', name: 'quotations', component: () => import('@/views/crm/Quotations.vue'), meta: { title: '报价单', permCode: 'menu:crm' } },
      { path: 'bid/intel', name: 'bidIntel', component: () => import('@/views/bid/BidIntel.vue'), meta: { title: '招标情报', permCode: 'menu:bid' } },
      { path: 'eng/portal', name: 'engPortal', component: () => import('@/views/eng/EngPortal.vue'), meta: { title: '工程交付', permCode: 'menu:eng' } },
      { path: 'eng/thd', name: 'thdPortal', component: () => import('@/views/eng/ThdPortal.vue'), meta: { title: '第三方门户', permCode: 'menu:eng' } },
      { path: 'mes/production', name: 'production', component: () => import('@/views/mes/Production.vue'), meta: { title: '生产与排产', permCode: 'menu:mes' } },
      { path: 'mes/aps', name: 'aps', component: () => import('@/views/mes/Aps.vue'), meta: { title: 'APS 排产', permCode: 'menu:mes' } },
      { path: 'cost/center', name: 'costCenter', component: () => import('@/views/cost/CostCenter.vue'), meta: { title: '成本中心', permCode: 'menu:cost' } },
      { path: 'kb/kanban', name: 'kanban', component: () => import('@/views/kb/Kanban.vue'), meta: { title: '九板看板', permCode: 'menu:kb' } },
      { path: 'arc/archives', name: 'archives', component: () => import('@/views/arc/Archives.vue'), meta: { title: '档案管理', permCode: 'menu:arc' } },
      { path: 'agent/console', name: 'agentConsole', component: () => import('@/views/agent/AgentConsole.vue'), meta: { title: 'Agent 接入台', permCode: 'menu:agt' } },
      { path: 'profile/appearance', name: 'appearance', component: () => import('@/views/profile/Appearance.vue'), meta: { title: '外观设置' } },
      { path: 'system/users', name: 'users', component: () => import('@/views/system/Users.vue'), meta: { title: '用户管理', permCode: 'menu:system:user' } },
      { path: 'system/roles', name: 'roles', component: () => import('@/views/system/Roles.vue'), meta: { title: '角色权限', permCode: 'menu:system:role' } },
      { path: 'system/depts', name: 'depts', component: () => import('@/views/system/Depts.vue'), meta: { title: '组织部门', permCode: 'menu:system:dept' } },
      { path: 'system/dicts', name: 'dicts', component: () => import('@/views/system/Dicts.vue'), meta: { title: '数据字典', permCode: 'menu:system:dict' } },
      { path: 'system/configs', name: 'configs', component: () => import('@/views/system/Configs.vue'), meta: { title: '系统参数', permCode: 'menu:system:config' } },
      { path: 'system/logs', name: 'logs', component: () => import('@/views/system/Logs.vue'), meta: { title: '审计日志', permCode: 'menu:system:log' } }
    ]
  },
  { path: '*', redirect: '/' }
]

const router = new VueRouter({ routes })

router.beforeEach(async (to, from, next) => {
  if (to.name === 'login') {
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
