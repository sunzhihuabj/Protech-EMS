<template>
  <!-- 会议纪要（FR-KB-11）：参会人/红灯数/结论/承诺/上报；只读可导出（打印走 W2/强制亮色） -->
  <div class="pl-page">
    <el-card shadow="never">
      <div slot="header" class="pl-meeting-head">
        <b>会议纪要 · {{ boardName }}</b>
        <span class="muted">{{ meetingDate }} · 时长 {{ durationMinutes }} 分钟</span>
        <div class="spacer"></div>
        <el-button size="small" icon="el-icon-download" @click="exportPdf">导出 PDF</el-button>
        <el-button size="small" icon="el-icon-printer" @click="$window && $window.print()">打印（自动亮色）</el-button>
      </div>

      <el-descriptions :column="5" size="small" border>
        <el-descriptions-item label="参会人">{{ host }}</el-descriptions-item>
        <el-descriptions-item label="红灯总数">{{ redTotal }}</el-descriptions-item>
        <el-descriptions-item label="过堂">{{ records.length }}</el-descriptions-item>
        <el-descriptions-item label="闭环">{{ closedCount }}</el-descriptions-item>
        <el-descriptions-item label="未闭环过堂">{{ skipped }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="minutesText" style="margin:12px 0;font-size:13px;color:var(--ink-2)">
        {{ minutesText }}
      </div>

      <h4 style="margin: 16px 0 8px">过堂结论（逐卡）</h4>
      <el-table :data="records" size="small">
        <el-table-column prop="title" label="卡事项" min-width="240" />
        <el-table-column prop="where" label="卡在哪" min-width="200" />
        <el-table-column prop="who" label="谁解决" width="120" />
        <el-table-column prop="when" label="何时解决" width="112" />
        <el-table-column prop="result" label="结论" width="110">
          <template slot-scope="{ row }">
            <pt-status-tag :value="row.result" :dict="'CLOSED:已闭环,SKIPPED:未闭环过堂'" />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!records.length" description="本次会议未逐卡记录（或从看板会议模式进入后自动生成）" :image-size="60" />
    </el-card>
  </div>
</template>

<script>
import { PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'

export default {
  name: 'MeetingMinutes',
  components: { PtStatusTag },
  data() {
    return {
      meeting: {},
      items: [],
      minutesText: ''
    }
  },
  computed: {
    // 服务端数据源（SPRINT-KB1）：/kb/meetings/{id} 落库纪要（FR-KB-11）
    boardName() {
      const m = this.meeting || {}
      return m.boardId != null ? '板 #' + m.boardId : '—'
    },
    meetingDate() { return (this.meeting || {}).meetingDate || '—' },
    durationMinutes() { return (this.meeting || {}).durationMinutes || 30 },
    host() { return (this.meeting || {}).attendees || '—' },
    redTotal() { return (this.meeting || {}).redCount || 0 },
    records() { return this.items },
    closedCount() { return (this.meeting || {}).closedCount || 0 },
    skipped() { return this.items.filter(r => r.result === 'SKIPPED').length }
  },
  mounted() {
    this.load()
    try { this.$store.dispatch('fetchMe').catch(() => {}) } catch (e) { /* 忽略 */ }
  },
  methods: {
    load() {
      const id = this.$route.params.id
      this.$http.get('/kb/meetings/' + id).then(body => {
        this.meeting = body.data.meeting || {}
        this.items = body.data.items || []
        this.minutesText = body.data.minutesText || ''
      }).catch(showError)
    },
    exportPdf() {
      const id = this.$route.params.id
      this.$http.get('/kb/meetings/' + id + '/pdf', { responseType: 'blob' }).then(blob => {
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = 'meeting-' + id + '.pdf'
        a.click()
        URL.revokeObjectURL(url)
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.pl-meeting-head { display: flex; align-items: center; gap: 12px; }
.pl-meeting-head .spacer { flex: 1; }
.muted { color: var(--ink-3); font-size: 12px; }
</style>
