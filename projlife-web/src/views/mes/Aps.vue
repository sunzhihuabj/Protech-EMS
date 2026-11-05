<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <!-- 产能 -->
      <el-tab-pane label="产能台账" name="capacity">
        <el-card shadow="never">
          <div slot="header"><b>资源产能（日产能=工时，A-33 口径）</b></div>
          <el-form inline size="small">
            <el-form-item label="资源ID"><el-input v-model.trim="capForm.resourceId" style="width: 90px" /></el-form-item>
            <el-form-item label="类型">
              <el-select v-model="capForm.resourceType" style="width: 120px">
                <el-option value="EQUIPMENT" label="设备" /><el-option value="PERSON" label="人员" /><el-option value="MOLD" label="模具" />
              </el-select>
            </el-form-item>
            <el-form-item label="日产能(h)"><el-input v-model.trim="capForm.capacityPerDay" style="width: 80px" /></el-form-item>
            <el-form-item><el-button v-permission="'aps:schedule'" size="small" type="primary" @click="addCapacity">登记</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- L1 排产 -->
      <el-tab-pane label="L1 顺排" name="l1">
        <el-card shadow="never">
          <!-- FR-APS：L1 粗排 优先级→订单→工序序顺排，最小负载选资源 -->
          <div slot="header"><b>L1 粗排</b></div>
          <el-form inline size="small">
            <el-form-item label="订单ID（逗号分隔）"><el-input v-model.trim="l1Form.orderIds" style="width: 240px" placeholder="如 3,4" /></el-form-item>
            <el-form-item label="开始日"><el-date-picker v-model="l1Form.startDate" type="date" value-format="yyyy-MM-dd" style="width: 140px" /></el-form-item>
            <el-form-item><el-button v-permission="'aps:schedule'" size="small" type="primary" @click="runL1">执行排产</el-button></el-form-item>
          </el-form>
          <el-descriptions v-if="l1Result" :column="4" size="small" border>
            <el-descriptions-item label="排产单">{{ l1Result.scheduleNo }}</el-descriptions-item>
            <el-descriptions-item label="明细数">{{ l1Result.detailCount }}</el-descriptions-item>
            <el-descriptions-item label="冲突数">
              <el-tag size="mini" :type="Number(l1Result.conflictCount) > 0 ? 'danger' : 'success'">{{ l1Result.conflictCount }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">{{ l1Result.status }}</el-descriptions-item>
          </el-descriptions>
          <el-alert v-for="(c, i) in (l1Result ? l1Result.conflicts : [])" :key="i" style="margin-top: 8px"
                    :title="'冲突：' + c.type + ' — ' + c.message" type="warning" :closable="false" show-icon />
        </el-card>
      </el-tab-pane>

      <!-- 明细与发布 -->
      <el-tab-pane label="明细与发布" name="detail">
        <div class="pl-toolbar">
          <span class="muted">{{ scheduleId ? '排产单 #' + scheduleId : '先执行 L1 排产' }}</span>
          <div class="spacer"></div>
          <el-button v-permission="'aps:schedule'" size="small" type="success" :disabled="!scheduleId" @click="publish">发布（齐套门禁）</el-button>
        </div>
        <pt-table :columns="detailCols" :data="details" :loading="detailLoading"
                  :total="0" aria-label="排产明细" empty-type="no-result" empty-label="排产明细">
        <template slot="actions" slot-scope="{ row }">
          <el-button v-permission="'aps:schedule'" size="mini" type="text" @click.stop="adjust(row)">拖拽调整</el-button>
        </template>
      </pt-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 调整弹窗 -->
    <el-dialog title="L2 人工调整（排产单置 ADJUSTED、版本 +1）" :visible.sync="adjustDialog" width="460px">
      <el-form label-width="100px" size="small">
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="adjustForm.plannedStart" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="adjustForm.plannedEnd" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="adjustDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveAdjust">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'

const GANTT_PX_PER_MIN = 0.4
import { showError } from '@/api/request'
import { PtTable, PtStatusTag } from '@/components/pt'

export default {
  name: 'ApsBoard',
  components: { PtTable, PtStatusTag },
  data() {
    return {
detailCols: [
        { prop: 'id', label: '明细', width: 70 },
        { prop: 'productionOrderId', label: '订单', width: 80 },
        { prop: 'processOrderId', label: '工序单', width: 90 },
        { prop: 'resourceId', label: '资源', width: 80 },
        { prop: 'plannedStart', label: '计划开始', width: 160 },
        { prop: 'plannedEnd', label: '计划结束', width: 160 },
        { prop: 'runMinutes', label: '工时(分)', width: 90, align: 'right' },
        { prop: 'status', label: '状态', width: 100 }
      ],
      tab: 'capacity',
      capForm: { resourceType: 'EQUIPMENT' },
      l1Form: { orderIds: '', startDate: '' },
      l1Result: null, scheduleId: null,
      details: [], detailLoading: false,
      adjustDialog: false, adjustForm: {}, adjustRow: null,
      dragging: null, dragStartX: 0, dragStartMin: 0, dragDeltaMin: 0
    }
  },
  methods: {
    timeToMinutes(iso) {
      if (!iso) return 0
      const t = new Date(String(iso).replace(' ', 'T')).getTime()
      return Math.max(0, (t - new Date().setHours(0, 0, 0, 0)) / 60000)
    },
    minutesToTime(min) {
      const d = new Date(new Date().setHours(0, 0, 0, 0) + min * 60000)
      const pad = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
    },
    ganttBarStyle(d) {
      const startMin = this.dragging && this.dragging.id === d.id
        ? this.dragStartMin + this.dragDeltaMin : this.timeToMinutes(d.plannedStart)
      const durMin = this.dragging && this.dragging.id === d.id
        ? Math.max(5, this.timeToMinutes(d.plannedEnd) - this.timeToMinutes(d.plannedStart))
        : Math.max(5, this.timeToMinutes(d.plannedEnd) - startMin)
      return {
        left: (startMin * GANTT_PX_PER_MIN) + 'px',
        width: Math.max(30, durMin * GANTT_PX_PER_MIN) + 'px'
      }
    },
    durText(d) {
      const s = this.dragging && this.dragging.id === d.id
        ? this.minutesToTime(this.dragStartMin + this.dragDeltaMin) : d.plannedStart
      return String(s).slice(5, 16)
    },
    startDrag(d, e) {
      this.dragging = d
      this.dragStartX = e.clientX
      this.dragStartMin = this.timeToMinutes(d.plannedStart)
      this.dragDeltaMin = 0
      e.preventDefault()
    },
    onGanttMove(e) {
      if (!this.dragging) return
      this.dragDeltaMin = Math.round((e.clientX - this.dragStartX) / GANTT_PX_PER_MIN)
    },
    onGanttUp() {
      if (!this.dragging) return
      const d = this.dragging
      const newStartMin = this.dragStartMin + this.dragDeltaMin
      const durMin = this.timeToMinutes(d.plannedEnd) - this.timeToMinutes(d.plannedStart)
      http.post(`/aps/details/${d.id}/adjust`, {
        plannedStart: this.minutesToTime(newStartMin),
        plannedEnd: this.minutesToTime(newStartMin + durMin)
      }).then(() => { this.$message.success('已调整'); this.loadDetails() })
        .catch(showError)
      this.dragging = null
    },
    addCapacity() {
      http.post('/aps/capacities', this.capForm).then(() => {
        this.$message.success('产能已登记')
      }).catch(showError)
    },
    runL1() {
      const ids = this.l1Form.orderIds.split(',').map(s => s.trim()).filter(Boolean)
      if (ids.length === 0) {
        this.$message.warning('请填订单 ID')
        return
      }
      http.post('/aps/schedules/l1', { productionOrderIds: ids, startDate: this.l1Form.startDate || undefined })
        .then(body => {
          this.l1Result = body.data
          this.scheduleId = body.data.scheduleId
          this.loadDetails()
          this.$message.success('排产完成：' + body.data.scheduleNo)
        }).catch(showError)
    },
    loadDetails() {
      if (!this.scheduleId) return
      this.detailLoading = true
      http.get(`/aps/schedules/${this.scheduleId}/details`).then(body => {
        this.details = body.data || []
      }).catch(showError).finally(() => { this.detailLoading = false })
    },
    adjust(row) {
      this.adjustRow = row
      this.adjustForm = {
        plannedStart: (row.plannedStart || '').replace('T', ' ').slice(0, 19),
        plannedEnd: (row.plannedEnd || '').replace('T', ' ').slice(0, 19)
      }
      this.adjustDialog = true
    },
    saveAdjust() {
      http.post(`/aps/details/${this.adjustRow.id}/adjust`, this.adjustForm).then(() => {
        this.$message.success('已调整（版本 +1，ADJUSTED）')
        this.adjustDialog = false
        this.loadDetails()
      }).catch(showError)
    },
    publish() {
      http.post(`/aps/schedules/${this.scheduleId}/publish`).then(body => {
        this.$notify.success({ title: '已发布', message: '排产单 ' + (body.data.scheduleNo || '') + ' → PUBLISHED' })
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.gantt { overflow-x: auto; padding: 8px 0; }
.gantt-row { display: flex; align-items: center; height: 36px; margin-bottom: 4px; }
.gantt-label { width: 140px; flex-shrink: 0; font-size: 12px; color: var(--ink-2); font-variant-numeric: tabular-nums; }
.gantt-track { flex: 1; height: 28px; background: var(--surface-3); border-radius: var(--r-1, 2px); position: relative; }
.gantt-bar { position: absolute; top: 2px; height: 24px; border-radius: var(--r-1, 2px); background: var(--pri-500); cursor: grab; min-width: 30px; display: flex; align-items: center; justify-content: center; }
.gantt-bar:active { cursor: grabbing; }
.gantt-bar.is-dragging { opacity: 0.85; box-shadow: var(--elev-2); }
.gantt-bar-text { font-size: 11px; color: var(--btn-pri-ink); font-variant-numeric: tabular-nums; white-space: nowrap; padding: 0 4px; }
.muted { color: var(--ink-3); font-size: 12px; }
</style>
