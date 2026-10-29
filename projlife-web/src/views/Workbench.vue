<template>
  <div class="pl-page">
    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="never">
          <div slot="header"><b>我的工作台</b></div>
          <p>欢迎，{{ me.realName }}（{{ me.userCode }}）· 数据范围：{{ dataScopeLabel }}</p>
          <p class="muted">
            S0 平台底座已交付：认证 / 权限 / 数据范围 / 审计 / 编码引擎 / 字典 / 通知 / 文件水印 / 12 套主题。
            业务切片（营销合同 → 项目 → 研发工艺 → 供应链 → 生产 → 质量 → 工程 → 看板）按 docs/DEVELOPMENT_PLAN.md 逐片推进。
          </p>
          <el-button size="small" type="primary" plain @click="$router.push('/profile/appearance')">
            外观设置（12 套主题）
          </el-button>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div slot="header"><b>最新通知</b></div>
          <div v-if="notifications.length === 0" class="empty">暂无通知（空状态约定：列表页无数据时渲染空态）</div>
          <div v-for="n in notifications" :key="n.id" class="notif">
            <span class="notif-title">{{ n.title }}</span>
            <span class="notif-time">{{ n.createdAt }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
const SCOPE_LABELS = { SELF: '本人', DEPT: '本部门', DEPT_SUB: '本部门及下级', PROJECT: '项目成员', ALL: '全局' }

export default {
  name: 'Workbench',
  data() {
    return { notifications: [] }
  },
  computed: {
    me() {
      return this.$store.state.user || {}
    },
    dataScopeLabel() {
      return SCOPE_LABELS[(this.$store.state.user || {}).dataScope] || '本人'
    }
  },
  mounted() {
    this.$http.get('/notifications', { params: { pageNum: 1, pageSize: 5 } }).then(body => {
      this.notifications = (body.data && body.data.records) || []
    }).catch(() => {})
  }
}
</script>

<style scoped>
.muted { color: #7b8794; font-size: 13px; }
.empty { color: #98a5b1; font-size: 13px; padding: 12px 0; }
.notif { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px dashed #eee; font-size: 13px; }
.notif-time { color: #98a5b1; }
</style>
