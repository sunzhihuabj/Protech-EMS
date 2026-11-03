<template>
  <!-- FR-QMS-08：量具台账（超期自动锁定 + 校准登记） -->
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-select v-model="query.status" size="small" style="width: 140px" clearable placeholder="状态" @change="onFilter">
          <el-option value="ACTIVE" label="在用" />
          <el-option value="FROZEN" label="超期锁定" />
        </el-select>
        <el-button size="small" type="primary" icon="el-icon-search" @click="onFilter">查询</el-button>
        <div class="spacer"></div>
        <span class="muted">超期未校准的量具自动锁定（检验下拉不可选）</span>
      </div>
      <pt-table :columns="cols" :data="rows" :loading="loading"
                :total="total" :page.sync="page"
                @page-change="onPage"
                aria-label="量具台账" empty-type="no-data" empty-label="量具">
      <template slot="cell-status" slot-scope="{ row }">
        <pt-status-tag :value="row.status || 'ACTIVE'" :dict="'ACTIVE:在用,FROZEN:超期锁定'" />
      </template>
      <template slot="cell-nextCalibrateDate" slot-scope="{ row }">
        <span :class="{ overdue: isOverdue(row.nextCalibrateDate) }">{{ row.nextCalibrateDate || '—' }}</span>
        <span v-if="isOverdue(row.nextCalibrateDate)" class="muted overdue-tip">（超期）</span>
      </template>
      <template slot="actions" slot-scope="{ row }">
        <el-button v-permission="'qms:gauge:manage'" size="mini" type="text" @click="calibrate(row)">校准登记</el-button>
      </template>
    </pt-table>
    </el-card>

    <!-- 校准登记弹窗 -->
    <el-dialog title="量具校准登记（FR-QMS-08）" :visible.sync="calDialog" width="420px">
      <el-form label-width="100px" size="small">
        <el-form-item label="量具">
          <span>{{ calTarget && calTarget.gaugeCode }} · {{ calTarget && calTarget.gaugeName }}</span>
        </el-form-item>
        <el-form-item label="校准结果" required>
          <el-select v-model="calForm.result" style="width: 100%">
            <el-option value="PASS" label="合格" />
            <el-option value="FAIL" label="不合格" />
          </el-select>
        </el-form-item>
        <el-form-item label="下次校准日" required>
          <el-date-picker v-model="calForm.nextDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" />
        </el-form-item>
        <el-form-item label="证书编号">
          <el-input v-model.trim="calForm.certificateNo" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="calDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doCalibrate">提交</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { PtTable, PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'

export default {
  name: 'QmsGauges',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      query: { pageNum: 1, pageSize: 20, status: '' },
      page: { pageNum: 1, pageSize: 20 },
      rows: [], loading: false, total: 0,
      cols: [
        { prop: 'gaugeCode', label: '编码', width: 140 },
        { prop: 'gaugeName', label: '名称', minWidth: 160 },
        { prop: 'model', label: '型号', width: 140 },
        { prop: 'precisionClass', label: '精度', width: 100 },
        { prop: 'calibrateCycleDays', label: '周期(天)', width: 90, align: 'right' },
        { prop: 'lastCalibrateDate', label: '上次校准', width: 120 },
        { prop: 'nextCalibrateDate', label: '下次校准', width: 130 },
        { prop: 'location', label: '位置', width: 140 },
        { prop: 'status', label: '状态', width: 110 }
      ],
      calDialog: false, calTarget: null, calForm: { result: 'PASS' }
    }
  },
  mounted() { this.load() },
  methods: {
    onPage(p) { this.query.pageNum = p.pageNum; this.query.pageSize = p.pageSize; this.load() },
    onFilter() { this.query.pageNum = 1; this.page.pageNum = 1; this.load() },
    load() {
      this.loading = true
      const params = { pageNum: this.query.pageNum, pageSize: this.query.pageSize }
      if (this.query.status) params.status = this.query.status
      this.$http.get('/qms/gauges', { params }).then(body => {
        const pageData = body.data || {}
        this.rows = pageData.records || []
        this.total = pageData.total || 0
      }).catch(showError).finally(() => { this.loading = false })
    },
    isOverdue(dateStr) {
      if (!dateStr) return false
      return new Date(dateStr) < new Date(new Date().toDateString())
    },
    calibrate(row) {
      this.calTarget = row
      const base = new Date()
      base.setDate(base.getDate() + (row.calibrateCycleDays || 180))
      this.calForm = { result: 'PASS', nextDate: base.toISOString().slice(0, 10), certificateNo: '' }
      this.calDialog = true
    },
    doCalibrate() {
      if (!this.calForm.nextDate) { this.$message.warning('下次校准日必填'); return }
      this.$http.post(`/qms/gauges/${this.calTarget.id}/calibrate`, {
        nextDate: this.calForm.nextDate,
        result: this.calForm.result,
        certificateNo: this.calForm.certificateNo
      }).then(() => {
        this.$message.success('校准已登记')
        this.calDialog = false
        this.load()
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.overdue { color: var(--danger, #f56c6c); font-weight: 600; }
.overdue-tip { margin-left: 4px; }
.muted { color: var(--ink-3); font-size: 12px; }
</style>
