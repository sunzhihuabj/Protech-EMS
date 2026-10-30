<template>
  <div class="pl-page">
    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="never">
          <div slot="header"><b>我的工作台 — {{ me.realName }}（{{ me.userCode }}）· 数据范围：{{ dataScopeLabel }} · 经营快照（FR-KB-12/06）</b></div>
          <el-row :gutter="12">
            <el-col :span="6" v-for="m in metricCards" :key="m.code">
              <div class="stat" :class="'stat-' + m.status">
                <div class="stat-label">{{ m.code }}</div>
                <div class="stat-value">{{ m.value }}<span class="stat-unit">/ {{ m.target }}</span></div>
                <div class="stat-tag">{{ m.status }}</div>
              </div>
            </el-col>
          </el-row>
          <el-divider content-position="left">九板红灯卡（BLOCKED）</el-divider>
          <el-table :data="redCards" size="mini" v-loading="cardLoading">
            <el-table-column prop="id" label="卡" width="60" />
            <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
            <el-table-column prop="ownerId" label="责任人" width="80" />
            <el-table-column prop="promiseDate" label="承诺日" width="110" />
            <el-table-column prop="status" label="状态" width="100">
              <template slot-scope="{ row }">
                <el-tag size="mini" type="danger">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="redCards.length === 0 && !cardLoading" description="无红灯卡，绿灯运行" :image-size="50" />
          <el-divider content-position="left">快捷入口</el-divider>
          <div class="quick-grid">
            <el-button size="small" plain @click="$router.push('/crm/opportunities')">商机</el-button>
            <el-button size="small" plain @click="$router.push('/crm/quotations')">报价</el-button>
            <el-button size="small" plain @click="$router.push('/bid/intel')">标讯</el-button>
            <el-button size="small" plain @click="$router.push('/inv/center')">库存</el-button>
            <el-button size="small" plain @click="$router.push('/eng/portal')">工程</el-button>
            <el-button size="small" plain @click="$router.push('/mes/aps')">排产</el-button>
            <el-button size="small" plain @click="$router.push('/kb/kanban')">九板</el-button>
            <el-button size="small" plain @click="$router.push('/profile/appearance')">外观（12 主题）</el-button>
          </div>
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
import http from '@/api/request'

const SCOPE_LABELS = { SELF: '本人', DEPT: '本部门', DEPT_SUB: '本部门及下级', PROJECT: '项目成员', ALL: '全局' }

/** 首页聚合指标编码（kb:metric:def 已定义并出快照即显示，未定义自动隐藏） */
const WATCH_METRICS = ['KB.DELIVERY', 'S17.MO.DELIVERY', 'SMOKE.DELIVERY']

export default {
  name: 'Workbench',
  data() {
    return {
      notifications: [],
      redCards: [], cardLoading: false,
      metricCards: []
    }
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
    this.loadRedCards()
    this.loadMetrics()
  },
  methods: {
    loadRedCards() {
      this.cardLoading = true
      http.get('/kb/cards', { params: { pageNum: 1, pageSize: 10, status: 'BLOCKED' } }).then(body => {
        this.redCards = (body.data && body.data.records) || []
      }).catch(() => {}).finally(() => { this.cardLoading = false })
    },
    loadMetrics() {
      WATCH_METRICS.forEach(code => {
        http.get(`/kb/metrics/${code}/trend`, { params: { periods: 1 } }).then(body => {
          const periods = (body.data && body.data.periods) || []
          if (periods.length > 0) {
            const p = periods[0]
            this.metricCards.push({
              code, value: p.metricValue, target: p.targetValue, status: p.status
            })
          }
        }).catch(() => {})
      })
    }
  }
}
</script>

<style scoped>
.muted { color: #7b8794; font-size: 13px; }
.empty { color: #98a5b1; font-size: 13px; padding: 12px 0; }
.notif { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px dashed #eee; font-size: 13px; }
.notif-time { color: #98a5b1; }
.header-actions { display: flex; justify-content: space-between; }
.stat { border: 1px solid #e5e9ef; border-radius: 6px; padding: 10px 12px; text-align: center; }
.stat-label { font-size: 12px; color: #8492a6; }
.stat-value { font-size: 22px; font-weight: 600; margin: 4px 0; }
.stat-unit { font-size: 12px; color: #8492a6; font-weight: 400; }
.stat-tag { font-size: 11px; }
.stat-GREEN .stat-tag { color: #67c23a; }
.stat-YELLOW .stat-tag { color: #e6a23c; }
.stat-RED .stat-tag { color: #f56c6c; }
.quick-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.quick-grid .el-button { margin-left: 0; }
</style>
