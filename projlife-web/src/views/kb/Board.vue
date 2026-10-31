<template>
  <!-- 九板看板（UID-03 §2.2 P3）：四区固定顺序 指标→红灯→进行→闭环；
       会议 ?mode=meeting（+2 档字号/议程条/计时器/三问）/ 大屏 ?mode=wall（只读+轮播 8—12s） -->
  <div class="kb-board" :class="{ 'is-meeting': isMeeting, 'is-wall': isWall }">
    <!-- 板头 56 -->
    <div class="kb-board-head">
      <span class="kb-board-name">{{ boardName }}</span>
      <span class="kb-board-meta">数据截至 {{ dataAsOf }}</span>
      <div class="spacer"></div>
      <el-button v-if="!isWall" size="small" @click="cycleMode">
        {{ isMeeting ? '退出会议' : '开始会议' }}
      </el-button>
    </div>

    <!-- 议程条（会议模式：72，四段） -->
    <div v-if="isMeeting" class="kb-agenda">
      <div v-for="seg in agenda" :key="seg.name" class="kb-agenda-seg"
           :class="{ 'is-active': seg.name === currentAgenda, 'is-overtime': segOvertime(seg) }">
        {{ seg.range }} {{ seg.name }}<template v-if="segOvertime(seg)"> · 超时 {{ segOvertimeText }}</template>
      </div>
      <div class="kb-timer" :class="{ 'is-overtime': segOvertime(segCurrent) }">
        {{ timerText }}
      </div>
    </div>

    <!-- 1 指标区（固定 140，3—5 张单行） -->
    <div class="kb-zone kb-zone-metric">
      <div class="kb-zone-head">指标（{{ metrics.length }}）</div>
      <div class="kb-metrics">
        <pt-metric v-for="m in metrics" :key="m.code" :label="m.name"
                   :target="m.target" :actual="m.actual" :state="m.light"
                   :precision="m.precision" @click="drill(m)" />
      </div>
    </div>

    <!-- 2 红灯区（弹性 0.55，最小 320；左 3px 竖条 + danger-bg 区底） -->
    <div class="kb-zone kb-zone-red" :style="{ flex: redFlex }">
      <div class="kb-zone-head is-red">红灯（{{ redCards.length }}）</div>
      <div class="kb-cards">
        <pt-board-card v-for="c in redCards" :key="c.id" :card="c" />
        <div v-if="redCards.length === 0" class="kb-empty-small" aria-label="无数据">今日无红灯</div>
      </div>
    </div>

    <!-- 3 进行区（弹性 0.45；缺项草稿聚集"待补全"） -->
    <div class="kb-zone kb-zone-doing" :style="{ flex: doingFlex }">
      <div class="kb-zone-head">进行中（{{ doingCards.length }}）</div>
      <div class="kb-cards">
        <pt-board-card v-for="c in doingCards" :key="c.id" :card="c" />
        <div v-if="doingCards.length === 0" class="kb-empty-small" aria-label="无数据">无进行中卡片</div>
      </div>
    </div>

    <!-- 4 闭环区（默认折叠条 48） -->
    <div class="kb-zone kb-zone-closed" :class="{ 'is-open': closedOpen }">
      <button class="kb-zone-head kb-closed-toggle" @click="closedOpen = !closedOpen">
        已闭环 {{ closedCards.length }} 张 {{ closedOpen ? '⌃' : '⌄' }}
      </button>
      <div v-if="closedOpen" class="kb-cards">
        <pt-board-card v-for="c in closedCards" :key="c.id" :card="c" dense />
      </div>
    </div>
  </div>
</template>

<script>
import { PtMetric, PtBoardCard } from '@/components/pt'
import http from '@/api/request'

const AGENDA = [
  { name: '指标速览', range: '0—3', start: 0 },
  { name: '红灯过堂', range: '3—22', start: 3 },
  { name: '今日承诺', range: '22—27', start: 22 },
  { name: '上报协调', range: '27—30', start: 27 }
]

export default {
  name: 'KbBoard',
  components: { PtMetric, PtBoardCard },
  data() {
    return {
      boardCode: this.$route.params.boardCode || 'TOTAL',
      metrics: [], redCards: [], doingCards: [], closedCards: [],
      dataAsOf: new Date().toLocaleString('zh-CN'),
      closedOpen: false,
      meetingStartedAt: null,
      now: Date.now(),
      nowTimer: null
    }
  },
  computed: {
    isMeeting() { return this.$route.query.mode === 'meeting' },
    isWall() { return this.$route.query.mode === 'wall' },
    boardName() {
      const names = { TOTAL: '经营总板', ORDER: '订单交付', SCM: '供应链', MES: '生产制造', ENG: '工程交付', PLM: '研发', SALES: '市场销售', FIN: '财务', ADMIN: '企管' }
      return (names[this.boardCode] || '经营看板') + ' · ' + this.boardCode
    },
    agenda() { return AGENDA },
    elapsedMin() { return this.meetingStartedAt ? (this.now - this.meetingStartedAt) / 60000 : 0 },
    segCurrent() {
      let cur = AGENDA[0]
      for (const seg of AGENDA) if (this.elapsedMin >= seg.start) cur = seg
      return cur
    },
    currentAgenda() { return this.segCurrent.name },
    segOvertimeText() {
      const elapsed = Math.floor(this.elapsedMin * 60)
      const end = this.segCurrent === AGENDA[AGENDA.length - 1] ? 30 * 60
        : AGENDA[AGENDA.indexOf(this.segCurrent) + 1].start * 60
      const over = elapsed - end
      if (over <= 0) return ''
      return `0:${String(over).padStart(2, '0')}`
    },
    timerText() {
      const elapsed = Math.floor(this.elapsedMin * 60)
      const segEnd = this.segCurrent === AGENDA[AGENDA.length - 1] ? 30 * 60
        : AGENDA[AGENDA.indexOf(this.segCurrent) + 1].start * 60
      const remain = Math.max(0, segEnd - elapsed)
      const mm = Math.floor(remain / 60); const ss = String(remain % 60).padStart(2, '0')
      return `${mm}:${ss}`
    },
    redFlex() { return '0 0 55%' },
    doingFlex() { return '0 0 45%' }
  },
  watch: {
    '$route.params.boardCode'() {
      this.boardCode = this.$route.params.boardCode || 'TOTAL'
      this.load()
    },
    '$route.query.mode'() { this.resetMeeting() }
  },
  mounted() {
    this.load()
    if (this.isMeeting || this.isWall) this.resetMeeting()
    this.nowTimer = setInterval(() => { this.now = Date.now() }, 1000)
  },
  beforeDestroy() { clearInterval(this.nowTimer) },
  methods: {
    segOvertime(seg) {
      if (!this.isMeeting || !this.meetingStartedAt) return false
      const idx = AGENDA.indexOf(seg)
      const segEnd = idx === AGENDA.length - 1 ? 30 : AGENDA[idx + 1].start
      return this.elapsedMin >= segEnd
    },
    resetMeeting() {
      this.meetingStartedAt = (this.isMeeting || this.isWall) ? Date.now() : null
    },
    cycleMode() {
      // 会议 ↔ 日常（wall 只读由 URL 直达；主题切换在 meeting/wall 下锁定由 Appearance 页判断）
      if (this.isMeeting) {
        this.$router.replace({ query: {} })
      } else {
        this.$router.replace({ query: { mode: 'meeting' } })
      }
    },
    load() {
      // 指标：kb_metric 快照（有则显示，未定义自动隐藏——不误报空态）
      http.get('/kb/metrics/S17.MO.DELIVERY/trend', { params: { periods: 1 } }).then(body => {
        const periods = (body.data && body.data.periods) || []
        if (periods.length) {
          const p = periods[0]
          this.metrics = [{
            code: 'S17.MO.DELIVERY', name: '订单准时交付率', target: Number(p.targetValue),
            actual: p.metricValue === null ? null : Number(p.metricValue),
            light: p.status, precision: 0
          }]
        } else {
          this.metrics = []
        }
      }).catch(() => {})
      // 卡池：红灯/进行/闭环（BLOCKED 亦入红灯区并列可见）
      http.get('/kb/cards', { params: { pageNum: 1, pageSize: 100 } }).then(body => {
        const rs = (body.data && body.data.records) || []
        const map = c => ({
          id: c.id, title: c.title, owner: c.ownerId ? 'E' + c.ownerId : '',
          dueAt: c.promiseDate, status: c.status, light: c.status,
          blockReason: c.blockReason, escalated: c.status === 'ESCALATED'
            || (c.blockedAt && Date.now() - new Date(c.blockedAt).getTime() > 3 * 86400000),
          staleDays: c.blockedAt ? Math.floor((Date.now() - new Date(c.blockedAt).getTime()) / 86400000) : 0
        })
        this.redCards = rs.filter(c => c.status === 'RED' || c.status === 'BLOCKED' || c.status === 'ESCALATED').map(map)
        this.doingCards = rs.filter(c => ['TODO', 'DOING', 'GREEN', 'YELLOW'].indexOf(c.status) !== -1).map(map)
        this.closedCards = rs.filter(c => c.status === 'CLOSED').map(map)
      }).catch(() => {})
    },
    drill(m) {
      this.$message.info(`下钻 ${m.name}：明细合计必须等于指标值（FR-KB-14）`)
    }
  }
}
</script>

<style scoped>
.kb-board { height: 100%; display: flex; flex-direction: column; gap: 8px; }
.kb-board-head {
  height: 56px; flex-shrink: 0; display: flex; align-items: center; gap: 16px;
  background: var(--surface); border: 1px solid var(--line-2); border-radius: var(--r-3, 6px);
  padding: 0 20px;
}
.kb-board-name { font-size: var(--fs-20, 20px); font-weight: 700; color: var(--ink); }
.kb-board-meta { font-size: 12px; color: var(--ink-3); }
.spacer { flex: 1; }
.kb-agenda {
  height: 72px; flex-shrink: 0; display: flex; align-items: center; gap: 8px;
  background: var(--surface); border: 1px solid var(--line-2); border-radius: var(--r-3, 6px);
  padding: 0 12px;
}
.kb-agenda-seg {
  flex: 1; text-align: center; padding: 8px 0; font-size: 13px; color: var(--ink-2);
  border-radius: var(--r-2, 4px);
}
.kb-agenda-seg.is-active { background: var(--pri-soft); color: var(--pri-text); font-weight: 600; border-bottom: 2px solid var(--pt-accent-graphic, var(--pri-500)); }
.kb-agenda-seg.is-overtime { background: var(--danger-bg); color: var(--danger-ink); }
.kb-timer { font-size: 24px; font-weight: 700; font-variant-numeric: tabular-nums; color: var(--ink); padding: 0 12px; }
.kb-timer.is-overtime { color: var(--danger-ink); background: var(--danger-bg); }
.kb-zone { border-radius: var(--r-3, 6px); display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.kb-zone-metric { flex: 0 0 140px; }
.kb-zone-red { background: var(--danger-bg); border-left: 3px solid var(--danger); min-height: 320px; }
.kb-zone-doing { background: var(--surface); border: 1px solid var(--line-2); }
.kb-zone-closed { background: var(--surface-2); flex: 0 0 auto; }
.kb-zone-head { padding: 8px 16px; font-size: 12px; font-weight: 600; color: var(--ink-2); }
.kb-zone-head.is-red { color: var(--danger-ink); }
.kb-closed-toggle { border: none; background: transparent; width: 100%; text-align: left; cursor: pointer; }
.kb-metrics { display: flex; gap: 16px; padding: 8px 16px 16px; }
.kb-metrics .pt-metric { flex: 1; }
.kb-cards { flex: 1; overflow-y: auto; padding: 8px 16px 16px; }
.kb-empty-small { text-align: center; color: var(--ink-3); font-size: 13px; padding: 24px 0; }
/* 会议模式：+2 档（正文 16/24、指标值 40/48），隐藏三问外录入控件 */
.kb-board.is-meeting { font-size: 16px; }
.kb-board.is-meeting .pt-metric-value { font-size: 40px; }
.kb-board.is-meeting .pt-bc-title { font-size: 16px; line-height: 24px; }
/* 大屏：只读（交互禁渲染由上层控制），自动轮播 8—12s 由路由参数驱动 */
</style>
