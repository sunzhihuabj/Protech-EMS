<template>
  <!-- 菜单节点：有子级递归，叶子项以 permCode 为路由高亮键（UID-02 §二 导航树） -->
  <el-submenu v-if="node.children && node.children.length" :index="node.permCode">
    <template slot="title">
      <span>{{ node.permName }}</span>
    </template>
    <sidebar-node v-for="child in node.children" :key="child.permCode" :node="child" />
  </el-submenu>
  <el-menu-item v-else :index="routeFor(node.permCode)">
    <span>{{ node.permName }}</span>
  </el-menu-item>
</template>

<script>
/** permCode → 路由映射（UID-05 §二 路由总表；后端菜单码 → 一级落位页） */
const ROUTE_BY_CODE = {
  'menu:workbench': '/workbench',
  'menu:crm': '/crm/opportunity',
  'menu:contract': '/ct/contract',
  'menu:project': '/prj/project',
  'menu:bid': '/bid/notice',
  'menu:plm': '/plm/bom',
  'menu:scm': '/scm/po',
  'menu:inv': '/inv/txn',
  'menu:mes': '/mes/order',
  'menu:qms': '/qms/inspection',
  'menu:eam': '/eam/asset',
  'menu:eng': '/eng/dispatch',
  'menu:cost': '/cost/report',
  'menu:kb': '/kb/board/TOTAL',
  'menu:mdm': '/mdm/project',
  'menu:arc': '/arc/archive',
  'menu:agt': '/agt/app',
  'menu:notification': '/workbench/message',
  'menu:system': '/sys/user',
  'menu:system:user': '/sys/user',
  'menu:system:role': '/sys/role',
  'menu:system:dept': '/sys/dept',
  'menu:system:dict': '/sys/dict',
  'menu:system:config': '/sys/config',
  'menu:system:log': '/sys/log',
  'menu:system:monitor': '/sys/monitor'
}

export default {
  name: 'SidebarNode',
  props: {
    node: { type: Object, required: true }
  },
  methods: {
    routeFor(code) {
      // 未登记的菜单码回落工作台（开发环境 console.error，UID-04 §五 fail-closed）
      if (!ROUTE_BY_CODE[code]) {
        if (process.env.NODE_ENV !== 'production') {
          console.error('[sidebar] 未登记菜单码:', code)
        }
        return '/workbench'
      }
      return ROUTE_BY_CODE[code]
    }
  }
}
</script>
