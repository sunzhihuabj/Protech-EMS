<template>
  <div class="pl-page">
    <!-- 头部 -->
    <div class="pl-doc-head">
      <span class="pl-doc-no">{{ project.projectNo || ('#' + id) }}</span>
      <span class="pl-doc-name">{{ project.projectName }}</span>
      <pt-status-tag :value="project.status" :dict="'PLANNED:计划中,ACTIVE:执行中,COMPLETED:已完工,CLOSED:已关闭,SUSPENDED:暂停'" />
      <span class="spacer"></span>
      <span class="muted">进度 {{ project.progressPct == null ? '—' : project.progressPct + '%' }}</span>
      <span class="muted">工期 {{ project.planStart }} ~ {{ project.planEnd }}</span>
    </div>

    <!-- 关键路径（CPM） -->
    <el-card shadow="never" :body-style="{ padding: '16px' }" style="margin-bottom:8px">
      <pt-form-section title="关键路径（CPM：松弛=0 即关键）" :error-count="0">
        <div class="muted" style="margin-bottom:8px">
          总工期 <b>{{ cpm.totalDurationDays == null ? '—' : cpm.totalDurationDays + ' 天' }}</b>
          · 关键任务 <b>{{ criticalCount }}</b>/{{ (cpm.tasks || []).length }}
        </div>
        <el-table :data="cpm.tasks || []" size="small" max-height="260">
          <el-table-column prop="wbsCode" label="WBS" width="110" />
          <el-table-column prop="taskName" label="任务" min-width="200" />
          <el-table-column prop="es" label="最早开始" width="104" />
          <el-table-column prop="ef" label="最早完成" width="104" />
          <el-table-column prop="slack" label="松弛(天)" width="88" align="right" />
          <el-table-column label="关键" width="70">
            <template slot-scope="{ row }">
              <span :class="row.critical ? 'cpm-yes' : 'cpm-no'">{{ row.critical ? '关键' : '—' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </pt-form-section>
    </el-card>

    <!-- 甘特（日刻度，拖拽右缘调整完成日） -->
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <pt-form-section title="WBS 甘特（拖拽条形 → 松手按 project:task:adjust_date 调整完成日）" :error-count="0">
        <div class="gantt-wrap" ref="ganttWrap" @mousemove="onDragMove" @mouseup="onDragEnd" @mouseleave="onDragEnd">
          <div v-for="row in ganttRows" :key="row.task.id" class="gantt-row">
            <div class="gantt-label" :title="row.task.taskName">{{ row.task.wbsCode }} {{ row.task.taskName }}</div>
            <div class="gantt-track">
              <div class="gantt-bar" :class="{ critical: row.critical, dragging: drag && drag.id === row.task.id }"
                   :style="{ left: row.leftPx + 'px', width: row.widthPx + 'px' }"
                   :disabled="!canAdjust"
                   @mousedown="onDragStart($event, row)">
                <span class="gantt-bar-text">{{ row.days }}d{{ row.critical ? ' ★' : '' }}</span>
              </div>
            </div>
          </div>
          <el-empty v-if="!ganttRows.length" description="项目暂无任务（任务计划在 WBS 分解中创建）" :image-size="60" />
        </div>
        <div class="muted" style="margin-top:8px">
          刻度 {{ PX_PER_DAY }}px/天 · 拖拽后按 project:task:adjust_date 持久化并重算 CPM
        </div>
      </pt-form-section>
    </el-card>
  </div>
</template>

<script>
import { PtFormSection, PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'

const PX_PER_DAY = 14

export default {
  name: 'ProjectDetail',
  components: { PtFormSection, PtStatusTag },
  data() {
    return {
      project: {},
      tasks: [],
      cpm: {},
      PX_PER_DAY,
      drag: null // {id, startX, deltaDays}
    }
  },
  computed: {
    id() { return this.$route.params.id },
    canAdjust() {
      return (this.$store.state.permissions || []).includes('project:task:adjust_date')
    },
    criticalCount() {
      return (this.cpm.tasks || []).filter(t => t.critical).length
    },
    ganttRows() {
      const withDates = this.tasks.filter(t => t.planStart && t.planEnd)
      if (!withDates.length) return []
      const min = withDates.map(t => t.planStart).sort()[0]
      const cpmById = {}
      ;(this.cpm.tasks || []).forEach(c => { cpmById[c.taskId] = c })
      return withDates.map(t => {
        const c = cpmById[t.id] || {}
        const leftDays = Math.round((new Date(t.planStart) - new Date(min)) / 86400000)
        const days = Math.max(1, Math.round((new Date(t.planEnd) - new Date(t.planStart)) / 86400000) + 1)
        return {
          task: t,
          critical: !!c.critical,
          leftPx: leftDays * PX_PER_DAY,
          widthPx: Math.max(20, days * PX_PER_DAY - 4),
          days
        }
      })
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.$http.get(`/project/${this.id}`).then(b => { this.project = b.data || {} }).catch(showError)
      this.$http.get(`/project/${this.id}/tasks`).then(b => { this.tasks = b.data || [] }).catch(showError)
      this.$http.get(`/project/${this.id}/critical-path`).then(b => {
        this.cpm = b.data || {}
      }).catch(() => { this.cpm = {} }) // 无任务/环时 CPM 422，甘特仍可看
    },
    onDragStart(e, row) {
      if (!this.canAdjust) return
      this.drag = { id: row.task.id, task: row.task, startX: e.clientX, deltaDays: 0 }
      e.preventDefault()
    },
    onDragMove(e) {
      if (!this.drag) return
      this.drag.deltaDays = Math.round((e.clientX - this.drag.startX) / PX_PER_DAY)
    },
    onDragEnd() {
      if (!this.drag) return
      const { id, task, deltaDays } = this.drag
      this.drag = null
      if (!deltaDays) return
      const newEnd = new Date(new Date(task.planEnd).getTime() + deltaDays * 86400000)
        .toISOString().slice(0, 10)
      this.$http.post(`/project/tasks/${id}/adjust-date`, { planEnd: newEnd }).then(() => {
        this.$message.success(`完成日调整为 ${newEnd}，CPM 已重算`)
        this.load()
      }).catch(err => {
        showError(err)
        this.load()
      })
    }
  }
}
</script>

<style scoped>
.pl-doc-head {
  height: 56px; display: flex; align-items: center; gap: 12px;
  background: var(--surface); border: 1px solid var(--line-2); border-radius: var(--r-3, 6px);
  padding: 0 20px; margin-bottom: 8px;
}
.pl-doc-no { font-family: var(--mono); font-size: 14px; color: var(--ink-2); }
.pl-doc-name { font-size: var(--fs-16, 16px); font-weight: 600; color: var(--ink); }
.spacer { flex: 1; }
.muted { font-size: 12px; color: var(--ink-3); }
.gantt-wrap { overflow-x: auto; padding: 4px 0; }
.gantt-row { display: flex; align-items: center; height: 34px; }
.gantt-label {
  width: 220px; flex: none; font-size: 12px; color: var(--ink-2);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; padding-right: 8px;
}
.gantt-track { position: relative; flex: 1; height: 26px; background: var(--surface-2, rgba(0,0,0,.03)); border-radius: 4px; }
.gantt-bar {
  position: absolute; top: 3px; height: 20px; border-radius: 4px;
  background: var(--primary, #409eff); opacity: .85; cursor: pointer;
  display: flex; align-items: center; overflow: hidden;
}
.gantt-bar.critical { background: var(--danger, #f56c6c); }
.gantt-bar.dragging { opacity: 1; box-shadow: 0 2px 8px rgba(0,0,0,.25); }
.gantt-bar-text { font-size: 11px; color: #fff; padding: 0 6px; white-space: nowrap; }
.cpm-yes { color: var(--danger, #f56c6c); font-weight: 600; font-size: 12px; }
.cpm-no { color: var(--ink-3); font-size: 12px; }
</style>
