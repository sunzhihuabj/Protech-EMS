<template>
  <!-- 菜单节点：有子级递归，叶子项以 permCode 为路由高亮键 -->
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
/** permCode → 路由路径映射（S0 固定表；后续切片随菜单种子扩展） */
const ROUTE_BY_CODE = {
  'menu:workbench': '/',
  'menu:notification': '/notifications',
  'menu:crm': '/crm/opportunities',
  'menu:contract': '/contract',
  'menu:system:user': '/system/users',
  'menu:system:role': '/system/roles',
  'menu:system:dept': '/system/depts',
  'menu:system:dict': '/system/dicts',
  'menu:system:config': '/system/configs',
  'menu:system:log': '/system/logs'
}

export default {
  name: 'SidebarNode',
  props: {
    node: { type: Object, required: true }
  },
  methods: {
    routeFor(code) {
      return ROUTE_BY_CODE[code] || '/'
    }
  }
}
</script>
