<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <span style="font-weight:600">指标口径</span>
        <div class="spacer"></div>
        <el-button v-permission="'kb:metric:def'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">定义指标</el-button>
      </div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="0"
                aria-label="指标口径" empty-type="no-data" empty-label="指标" empty-action-text="定义指标" @empty-action="dialog = true">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status || 'GREEN'" :dict="'GREEN:正常,YELLOW:关注,RED:红灯'" />
        </template>
        <template slot="actions" slot-scope="{ row }">
          <el-button v-permission="'kb:metric:def'" size="mini" type="text" @click="openSnap(row)">手动快照</el-button>
        </template>
      </pt-table>
    </el-card>
    <el-dialog title="定义指标" :visible.sync="dialog" width="440px">
      <el-form label-width="100px" size="small">
        <el-form-item label="指标编码" required><el-input v-model.trim="form.metricCode" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model.trim="form.metricName" /></el-form-item>
        <el-form-item label="看板ID" required><el-input v-model.trim="form.boardId" /></el-form-item>
        <el-form-item label="目标值"><el-input v-model.trim="form.target" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">定义</el-button>
      </div>
    </el-dialog>
    <el-dialog title="指标手动快照" :visible.sync="snapDialog" width="400px">
      <el-form label-width="100px" size="small">
        <el-form-item label="指标">
          <span>{{ snapTarget && snapTarget.metricCode }} · {{ snapTarget && snapTarget.metricName }}</span>
        </el-form-item>
        <el-form-item label="期间" required>
          <el-date-picker v-model="snapForm.period" type="month" value-format="yyyy-MM" placeholder="yyyy-MM" style="width: 100%" />
        </el-form-item>
        <el-form-item label="取值" required>
          <el-input v-model.trim="snapForm.value" placeholder="指标值（数值）" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="snapDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doSnap">落快照</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { PtTable, PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'
export default {
  name: 'KbMetricDefs',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      columns: [
        { prop: 'metricCode', label: '编码' },
        { prop: 'metricName', label: '名称' },
        { prop: 'target', label: '目标' },
        { prop: 'status', label: '灯色', width: 80 }
      ],
      records: [], loading: false, dialog: false, form: {},
      snapDialog: false, snapTarget: null, snapForm: { period: '', value: '' }
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/kb/metrics/S17.MO.DELIVERY/trend', { params: { periods: 12 } }).then(body => {
        this.records = (body.data && body.data.periods) || []
      }).catch(showError).finally(() => { this.loading = false })
    },
    create() {
      this.$http.post('/kb/metrics', this.form).then(() => {
        this.$message.success('指标已定义'); this.dialog = false; this.load()
      }).catch(showError)
    },
    openSnap(row) {
      this.snapTarget = row
      const d = new Date()
      this.snapForm = { period: d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0'), value: '' }
      this.snapDialog = true
    },
    doSnap() {
      if (!this.snapForm.period) { this.$message.warning('期间必填'); return }
      if (this.snapForm.value === '' || isNaN(Number(this.snapForm.value))) { this.$message.warning('取值必须为数值'); return }
      this.$http.post(`/kb/metrics/${this.snapTarget.metricCode}/snapshots`, {
        period: this.snapForm.period,
        value: Number(this.snapForm.value)
      }).then(() => {
        this.$message.success('快照已落库（灯色按目标重算）')
        this.snapDialog = false
        this.load()
      }).catch(showError)
    }
  }
}
</script>
