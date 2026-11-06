<template>
  <div class="pl-page wb">
    <!-- 问候区（P1：页面标题层级 + 数据范围徽章；需求编号外露清除） -->
    <div class="wb-hero">
      <div>
        <h2 class="wb-hello">{{ greeting }}，{{ me.realName || me.username }}</h2>
        <p class="wb-sub">
          <el-tag size="mini" class="wb-scope-tag" effect="plain">{{ dataScopeLabel }}数据</el-tag>
          <span>今天是 {{ todayText }}，经营快照已就绪</span>
        </p>
      </div>
      <div class="wb-hero-actions">
        <el-button size="small" icon="el-icon-refresh" plain @click="reloadAll">刷新快照</el-button>
      </div>
    </div>

    <!-- 指标卡：中文名 + 大数值 + 环比 + 状态色左条（令牌取色） -->
    <div class="wb-metrics" v-loading="metricsLoading">
      <div v-for="m in metricCards" :key="m.code" class="wb-stat" :class="'is-' + (m.status || 'GREY').toLowerCase()">
        <div class="wb-stat-label">{{ metricName(m.code) }}</div>
        <div class="wb-stat-value">
          {{ m.value }}<span class="wb-stat-target"> / {{ m.target }}</span>
          <span v-if="m.trend != null" class="wb-stat-trend" :class="m.trend >= 0 ? 'up' : 'down'">
            <i :class="m.trend >= 0 ? 'el-icon-top' : 'el-icon-bottom'"></i>{{ Math.abs(m.trend) }}%
          </span>
        </div>
        <!-- SPRINT-UI-2：12 期迷你趋势（SVG polyline，描边走令牌；单点不画） -->
        <svg v-if="m.spark && m.spark.length > 1" class="wb-spark" viewBox="0 0 100 26"
             preserveAspectRatio="none" aria-hidden="true">
          <polyline :points="sparkPoints(m.spark)" class="wb-spark-line" />
          <circle :cx="sparkXY(m.spark).x" :cy="sparkXY(m.spark).y" r="2.4" class="wb-spark-dot" />
        </svg>
        <div class="wb-stat-foot">{{ m.code }}</div>
      </div>
      <div v-if="metricCards.length === 0 && !metricsLoading" class="wb-stat wb-stat--empty">
        <div class="wb-stat-label">经营快照</div>
        <div class="wb-stat-empty-text">指标定义后此处自动显示</div>
      </div>
    </div>

    <el-row :gutter="16" class="wb-row">
      <el-col :span="16">
        <el-card shadow="never" class="wb-card">
          <div slot="header" class="wb-card-head">
            <span class="wb-card-title">九板红灯卡</span>
            <span class="wb-card-sub">BLOCKED · 需责任人跟催</span>
            <el-button class="wb-card-op" type="text" size="mini" @click="$router.push('/kb/board/TOTAL')">查看九板</el-button>
          </div>
          <el-table :data="redCards" size="mini" v-loading="cardLoading">
            <el-table-column label="" width="36">
              <template slot-scope=""><span class="wb-dot wb-dot--red"></span></template>
            </el-table-column>
            <el-table-column prop="id" label="卡" width="64" />
            <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
            <el-table-column prop="ownerId" label="责任人" width="80" />
            <el-table-column prop="promiseDate" label="承诺日" width="110">
              <template slot-scope="{ row }">
                <span :class="{ 'wb-overdue': isOverdue(row.promiseDate) }">{{ row.promiseDate || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="96">
              <template slot-scope="{ row }">
                <el-tag size="mini" type="danger" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="redCards.length === 0 && !cardLoading" description="无红灯卡，绿灯运行" :image-size="50" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="wb-card">
          <div slot="header" class="wb-card-head">
            <span class="wb-card-title">最新通知</span>
            <el-button class="wb-card-op" type="text" size="mini" @click="$router.push('/workbench/message')">全部</el-button>
          </div>
          <div v-if="notifications.length === 0 && !notifLoading" class="wb-empty">暂无通知</div>
          <div v-for="n in notifications" :key="n.id" class="wb-notif" :class="{ 'is-unread': n.isRead === 0 }">
            <i class="el-icon-chat-dot-square wb-notif-ico"></i>
            <span class="wb-notif-title">{{ n.title }}</span>
            <span class="wb-notif-time">{{ shortTime(n.createdAt) }}</span>
          </div>
          <div v-loading="notifLoading" class="wb-notif-loading"></div>
        </el-card>

        <el-card shadow="never" class="wb-card">
          <div slot="header" class="wb-card-head">
            <span class="wb-card-title">快捷入口</span>
          </div>
          <div class="wb-quick">
            <button v-for="q in quickLinks" :key="q.path" class="wb-quick-item" @click="$router.push(q.path)">
              <i :class="q.icon"></i>
              <span>{{ q.label }}</span>
            </button>
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
/** 指标中文名（未知编码回退展示原编码） */
const METRIC_NAMES = {
  'KB.DELIVERY': '九板交付达成率',
  'S17.MO.DELIVERY': '生产订单准交率',
  'SMOKE.DELIVERY': '冒烟交付达成率'
}

const STATUS_LABELS = { BLOCKED: '受阻', OPEN: '进行中', CLOSED: '已关闭' }

export default {
  name: 'Workbench',
  data() {
    return {
      notifications: [], notifLoading: false,
      redCards: [], cardLoading: false,
      metricCards: [], metricsLoading: false
    }
  },
  computed: {
    me() {
      return this.$store.state.user || {}
    },
    dataScopeLabel() {
      return SCOPE_LABELS[(this.$store.state.user || {}).dataScope] || '本人'
    },
    greeting() {
      const h = new Date().getHours()
      if (h < 6) return '夜深了'
      if (h < 12) return '早上好'
      if (h < 14) return '中午好'
      if (h < 18) return '下午好'
      return '晚上好'
    },
    todayText() {
      const d = new Date()
      const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
      return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · 周${week}`
    },
    quickLinks() {
      return [
        { path: '/crm/opportunity', label: '商机', icon: 'el-icon-s-data' },
        { path: '/crm/quotations', label: '报价', icon: 'el-icon-document-copy' },
        { path: '/bid/notice', label: '标讯', icon: 'el-icon-megaphone' },
        { path: '/inv/txn', label: '库存', icon: 'el-icon-box' },
        { path: '/eng/dispatch', label: '工程', icon: 'el-icon-set-up' },
        { path: '/aps/schedule', label: '排产', icon: 'el-icon-date' },
        { path: '/kb/board/TOTAL', label: '九板', icon: 'el-icon-data-board' },
        { path: '/profile/appearance', label: '外观', icon: 'el-icon-magic-stick' }
      ]
    }
  },
  mounted() {
    this.reloadAll()
  },
  methods: {
    reloadAll() {
      this.loadNotifications()
      this.loadRedCards()
      this.loadMetrics()
    },
    loadNotifications() {
      this.notifLoading = true
      this.$http.get('/notifications', { params: { pageNum: 1, pageSize: 5 } }).then(body => {
        this.notifications = (body.data && body.data.records) || []
      }).catch(() => {}).finally(() => { this.notifLoading = false })
    },
    loadRedCards() {
      this.cardLoading = true
      http.get('/kb/cards', { params: { pageNum: 1, pageSize: 10, status: 'BLOCKED' } }).then(body => {
        this.redCards = (body.data && body.data.records) || []
      }).catch(() => {}).finally(() => { this.cardLoading = false })
    },
    loadMetrics() {
      this.metricsLoading = true
      // periods=12：全序列画 sparkline，末两期算环比（SPRINT-UI-2）
      Promise.all(WATCH_METRICS.map(code =>
        http.get(`/kb/metrics/${code}/trend`, { params: { periods: 12 } })
          .then(body => {
            const periods = (body.data && body.data.periods) || []
            if (periods.length === 0) return null
            const cur = periods[periods.length - 1]
            const prev = periods.length > 1 ? periods[periods.length - 2] : null
            let trend = null
            if (prev && prev.metricValue != null && cur.metricValue != null && Number(prev.metricValue) !== 0) {
              trend = Math.round((Number(cur.metricValue) - Number(prev.metricValue)) / Math.abs(Number(prev.metricValue)) * 100)
            }
            const spark = periods.map(p => (p.metricValue == null ? null : Number(p.metricValue)))
              .filter(v => v != null)
            return { code, value: cur.metricValue, target: cur.targetValue, status: cur.status, trend, spark }
          }).catch(() => null)
      )).then(list => {
        this.metricCards = list.filter(Boolean)
      }).finally(() => { this.metricsLoading = false })
    },
    /** sparkline 归一化：x 均分 0-100，y 按 min/max 压入 3-23（留边距） */
    sparkXY(series) {
      const n = series.length
      const min = Math.min(...series)
      const max = Math.max(...series)
      const span = max - min || 1
      const x = 100
      const y = 23 - (series[n - 1] - min) / span * 20
      return { x, y }
    },
    sparkPoints(series) {
      const n = series.length
      const min = Math.min(...series)
      const max = Math.max(...series)
      const span = max - min || 1
      return series.map((v, i) => {
        const x = n === 1 ? 0 : (i / (n - 1)) * 100
        const y = 23 - (v - min) / span * 20
        return x.toFixed(1) + ',' + y.toFixed(1)
      }).join(' ')
    },
    metricName(code) {
      return METRIC_NAMES[code] || code
    },
    statusLabel(s) {
      return STATUS_LABELS[s] || s
    },
    isOverdue(dateStr) {
      if (!dateStr) return false
      const d = new Date(String(dateStr).replace(/-/g, '/'))
      return !isNaN(d.getTime()) && d.getTime() < Date.now()
    },
    shortTime(t) {
      if (!t) return ''
      return String(t).length >= 16 ? String(t).slice(5, 16) : String(t)
    }
  }
}
</script>

<style scoped>
/* 全部取色走主题令牌（UID-01：禁自创 hex），暗色主题自动成立 */
.wb { padding-bottom: 24px; }

/* --- 问候区 --- */
.wb-hero { display: flex; align-items: flex-end; justify-content: space-between; padding: 4px 4px 16px; }
.wb-hello { margin: 0 0 6px; font-size: 20px; font-weight: 600; color: var(--ink); letter-spacing: .5px; }
.wb-sub { margin: 0; font-size: 13px; color: var(--ink-3); display: flex; align-items: center; gap: 8px; }
.wb-scope-tag { margin-right: 2px; }

/* --- 指标卡：状态色左条 + 数值层级 + 环比 --- */
.wb-metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; margin-bottom: 16px; }
.wb-stat {
  position: relative; background: var(--surface); border: 1px solid var(--line);
  border-radius: var(--r-3); padding: 14px 16px 12px 18px; overflow: hidden;
  transition: box-shadow 180ms ease, transform 180ms ease;
}
.wb-stat::before {
  content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 3px;
  background: var(--line-2);
}
.wb-stat.is-green::before { background: var(--ok); }
.wb-stat.is-yellow::before { background: var(--warn); }
.wb-stat.is-red::before { background: var(--danger); }
.wb-stat:hover { box-shadow: var(--elev-2); transform: translateY(-1px); }
.wb-stat-label { font-size: 12px; color: var(--ink-3); }
.wb-stat-value { font-size: 26px; font-weight: 600; color: var(--ink); margin: 6px 0 4px; font-variant-numeric: tabular-nums; line-height: 1.2; }
.wb-stat-target { font-size: 12px; color: var(--ink-4); font-weight: 400; }
.wb-stat-trend { font-size: 12px; font-weight: 600; margin-left: 8px; }
.wb-stat-trend.up { color: var(--down-ink, var(--ok)); }
.wb-stat-trend.down { color: var(--up-ink, var(--danger)); }
.wb-stat-foot { font-family: var(--mono); font-size: 11px; color: var(--ink-4); }
.wb-stat--empty { display: flex; flex-direction: column; justify-content: center; }
.wb-stat-empty-text { font-size: 13px; color: var(--ink-4); margin-top: 6px; }

/* --- SPRINT-UI-2：12 期 sparkline（卡片右下角淡入，不抢数值主体） --- */
.wb-spark {
  position: absolute; right: 12px; bottom: 10px; width: 88px; height: 26px;
  opacity: .75; pointer-events: none;
}
.wb-spark-line {
  fill: none; stroke: var(--pri-400, var(--ink-3)); stroke-width: 1.6;
  stroke-linecap: round; stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}
.wb-spark-dot { fill: var(--pri-500, var(--ink-2)); }

/* --- 卡片头三段式：标题 / 副题 / 操作 --- */
.wb-card { border-color: var(--line); }
.wb-card-head { display: flex; align-items: baseline; gap: 8px; }
.wb-card-title { font-weight: 600; color: var(--ink); }
.wb-card-sub { font-size: 12px; color: var(--ink-4); flex: 1; }
.wb-card-op { padding: 0; }

.wb-row { margin-top: 2px; }

/* --- 红灯卡 --- */
.wb-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; }
.wb-dot--red { background: var(--danger); box-shadow: 0 0 0 3px var(--danger-bg); }
.wb-overdue { color: var(--danger-ink); font-weight: 600; font-variant-numeric: tabular-nums; }

/* --- 通知 --- */
.wb-empty { color: var(--ink-4); font-size: 13px; padding: 24px 0; text-align: center; }
.wb-notif { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px dashed var(--line); font-size: 13px; color: var(--ink-2); }
.wb-notif:last-of-type { border-bottom: none; }
.wb-notif-ico { color: var(--ink-4); flex-shrink: 0; }
.wb-notif.is-unread .wb-notif-title { color: var(--ink); font-weight: 600; }
.wb-notif-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wb-notif-time { font-size: 12px; color: var(--ink-4); font-variant-numeric: tabular-nums; flex-shrink: 0; }
.wb-notif-loading { min-height: 0; }

/* --- 快捷入口：图标磁贴 --- */
.wb-quick { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.wb-quick-item {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 12px 4px 10px; border: 1px solid var(--line); border-radius: var(--r-3);
  background: var(--surface); cursor: pointer; font-size: 12px; color: var(--ink-2);
  font-family: inherit; transition: box-shadow 180ms ease, border-color 180ms ease, transform 180ms ease, color 180ms ease;
}
.wb-quick-item i { font-size: 20px; color: var(--pri-500); }
.wb-quick-item:hover {
  border-color: var(--pri-300); box-shadow: var(--elev-1);
  transform: translateY(-1px); color: var(--pri-text);
}
</style>
