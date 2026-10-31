<template>
  <!-- 会议纪要（FR-KB-11）：参会人/红灯数/结论/承诺/上报；只读可导出（打印走 W2/强制亮色） -->
  <div class="pl-page">
    <el-card shadow="never">
      <div slot="header" class="pl-meeting-head">
        <b>会议纪要 · {{ boardCode }} 板</b>
        <span class="muted">{{ meetingDate }} · 时长 30 分钟议程</span>
        <div class="spacer"></div>
        <el-button size="small" icon="el-icon-printer" @click="$window && $window.print()">打印（自动亮色）</el-button>
      </div>

      <el-descriptions :column="4" size="small" border>
        <el-descriptions-item label="参会人">{{ host }}</el-descriptions-item>
        <el-descriptions-item label="红灯总数">{{ redTotal }}</el-descriptions-item>
        <el-descriptions-item label="闭环">{{ closedCount }}</el-descriptions-item>
        <el-descriptions-item label="未闭环过堂">{{ skipped }}</el-descriptions-item>
      </el-descriptions>

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

export default {
  name: 'MeetingMinutes',
  components: { PtStatusTag },
  data() {
    return {
      boardCode: this.$route.params.id || 'TOTAL',
      meetingDate: new Date().toLocaleString('zh-CN'),
      host: '',
      records: []
    }
  },
  computed: {
    redTotal() { return this.records.length },
    closedCount() { return this.records.filter(r => r.result === 'CLOSED').length },
    skipped() { return this.records.filter(r => r.result === 'SKIPPED').length }
  },
  mounted() {
    // 纪要来源：会议模式的逐卡记录（sessionStorage，FR-KB-11 骨架；生产落 kb_meeting 表）
    try {
      const saved = JSON.parse(sessionStorage.getItem('pl:kb:meeting:' + this.boardCode) || 'null')
      this.records = (saved && saved.records) || []
      this.host = (saved && saved.host) || (this.$store.state.user || {}).realName || '—'
    } catch (e) { /* 忽略 */ }
    try { this.$store.dispatch('fetchMe').catch(() => {}) } catch (e) { /* 忽略 */ }
  }
}
</script>

<style scoped>
.pl-meeting-head { display: flex; align-items: center; gap: 12px; }
.pl-meeting-head .spacer { flex: 1; }
.muted { color: var(--ink-3); font-size: 12px; }
</style>
