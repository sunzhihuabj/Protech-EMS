<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <!-- 生产订单 -->
      <el-tab-pane label="生产订单" name="orders">
        <div class="pl-toolbar">
          <el-button v-permission="'mes:order:create'" size="small" type="primary" icon="el-icon-plus" @click="moDialog = true">新建 MO（三类型）</el-button>
          <el-button size="small" icon="el-icon-refresh" @click="loadOrders">刷新</el-button>
        </div>
        <pt-table :columns="moCols" :data="orders" :loading="moLoading"
                  :total="0" aria-label="生产订单台账" empty-type="no-data"
                  empty-label="生产订单" empty-action-text="新建 MO" @empty-action="moDialog = true"
                  @row-click="loadProcesses">
        <template slot="cell-moType" slot-scope="{ row }">
          <pt-status-tag :value="row.moType" :dict="'STANDARD:标准,BATCH:批量,ENGINEERING:工程'" />
        </template>
        <template slot="actions" slot-scope="{ row }">
          <el-button size="mini" type="text" @click="materialCheck(row)">齐套检查</el-button>
        </template>
      </pt-table>
      </el-tab-pane>

      <!-- 工序 -->
      <el-tab-pane label="工序与报工" name="processes">
        <div class="pl-toolbar">
          <span class="muted">{{ currentMo ? '当前 MO：' + currentMo.moNo : '点击上方订单查看工序' }}</span>
        </div>
        <el-table :data="processes" v-loading="procLoading" size="small">
          <el-table-column prop="processNo" label="工序单" width="160" />
          <el-table-column prop="processSeq" label="序" width="60" />
          <el-table-column prop="processName" label="工序" width="130" />
          <el-table-column prop="standardHours" label="标准工时" width="90" />
          <el-table-column prop="plannedQty" label="计划量" width="90" />
          <el-table-column prop="goodQty" label="合格" width="80" />
          <el-table-column prop="status" label="状态" width="120">
            <template slot-scope="{ row }">
              <el-tag size="mini" :type="row.status === 'FINISHED' ? 'success' : row.status === 'STARTED' ? '' : 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template slot-scope="{ row }">
              <el-button v-if="row.status === 'PENDING'" v-permission="'mes:process:work'" size="mini" type="text" @click="start(row)">开工</el-button>
              <el-button v-if="row.status === 'STARTED'" v-permission="'mes:process:work'" size="mini" type="text" @click="finish(row)">报工</el-button>
              <el-button v-if="row.status === 'FINISHED'" v-permission="'mes:qc:release'" size="mini" type="text" @click="openQc(row)">质检</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Andon -->
      <el-tab-pane label="Andon" name="andon">
        <div class="pl-toolbar">
          <el-button v-permission="'mes:andon:raise'" size="small" type="danger" icon="el-icon-warning-outline" @click="andonDialog = true">拉响 Andon</el-button>
        </div>
        <p class="muted">分级升级（工位→班长→主任）由 job 扫描推进；升级记录见通知中心。</p>
      </el-tab-pane>
    </el-tabs>

    <!-- MO 弹窗 -->
    <el-dialog title="新建生产订单（STANDARD/BATCH/ENGINEERING）" :visible.sync="moDialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="类型" required>
          <el-select v-model="moForm.moType" style="width: 100%">
            <el-option value="STANDARD" label="标准 STANDARD" /><el-option value="BATCH" label="批量 BATCH" /><el-option value="ENGINEERING" label="工程 ENGINEERING" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目ID" required><el-input v-model.trim="moForm.projectId" /></el-form-item>
        <el-form-item label="BOM头ID" required><el-input v-model.trim="moForm.bomHeaderId" /></el-form-item>
        <el-form-item label="计划量" required><el-input v-model.trim="moForm.planQty" /></el-form-item>
        <el-form-item label="计划开始" required><el-date-picker v-model="moForm.planStart" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
        <el-form-item label="计划结束" required><el-date-picker v-model="moForm.planEnd" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="moDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createMo">创建</el-button>
      </div>
    </el-dialog>

    <!-- Andon 弹窗 -->
    <el-dialog title="拉响 Andon" :visible.sync="andonDialog" width="460px">
      <el-form label-width="100px" size="small">
        <el-form-item label="订单ID" required><el-input v-model.trim="andonForm.productionOrderId" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="andonForm.alertType" style="width: 100%">
            <el-option value="QUALITY" label="质量" /><el-option value="EQUIPMENT" label="设备" /><el-option value="MATERIAL" label="物料" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required><el-input v-model="andonForm.content" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="andonDialog = false">取消</el-button>
        <el-button size="small" type="danger" @click="raiseAndon">拉响</el-button>
      </div>
    </el-dialog>
    <!-- 齐套检查结果弹窗（FR-APS-05 口径说明版） -->
    <el-dialog title="齐套检查（FR-APS-05）" :visible.sync="mcDialog" width="420px">
      <div v-if="mcResult">
        <p style="margin: 0 0 8px"><b>{{ mcResult.moNo }}</b> · 计划量 {{ mcResult.planQty }}</p>
        <el-alert :title="mcResult.message" :type="mcResult.message && mcResult.message.indexOf('齐套') !== -1 ? 'success' : 'warning'" :closable="false" show-icon />
        <p class="muted" style="margin-bottom: 0">精确到物料行的齐套明细在 APS 排产（aps_material_check）。</p>
      </div>
      <div slot="footer">
        <el-button size="small" type="primary" @click="mcDialog = false">知道了</el-button>
      </div>
    </el-dialog>

    <!-- 工序质检弹窗（FR-MES-16 检验放行） -->
    <el-dialog title="工序质检（FR-MES-16 检验放行）" :visible.sync="qcDialog" width="440px">
      <div v-if="qcTarget" style="margin-bottom: 12px">
        <span class="muted">{{ qcTarget.processNo }} · {{ qcTarget.processName }} · 合格 {{ qcTarget.goodQty }}</span>
      </div>
      <el-form label-width="90px" size="small">
        <el-form-item label="质检结论" required>
          <el-radio-group v-model="qcForm.passed">
            <el-radio :label="true">合格放行</el-radio>
            <el-radio :label="false">不合格退回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="qcForm.passed === false" label="退回原因" required>
          <el-input v-model.trim="qcForm.rejectReason" type="textarea" :rows="2" placeholder="不合格描述（退回重开工）" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="qcDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doQc">提交</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'
import { PtTable, PtStatusTag } from '@/components/pt'

export default {
  name: 'Production',
  components: { PtTable, PtStatusTag },
  data() {
    return {
moCols: [
        { prop: 'moNo', label: 'MO 编号', width: 160 },
        { prop: 'moType', label: '类型', width: 110 },
        { prop: 'planQty', label: '计划量', width: 90, align: 'right' },
        { prop: 'finishedQty', label: '完工量', width: 90, align: 'right' },
        { prop: 'planStart', label: '计划开始', width: 112 },
        { prop: 'planEnd', label: '计划结束', width: 112 },
        { prop: 'status', label: '状态', width: 120 }
      ],
      tab: 'orders',
      orders: [], moLoading: false, moDialog: false, moForm: { moType: 'BATCH' },
      currentMo: null, processes: [], procLoading: false,
      andonDialog: false, andonForm: { alertType: 'QUALITY' },
      mcDialog: false, mcResult: null,
      qcDialog: false, qcTarget: null, qcForm: { passed: true, rejectReason: '' }
    }
  },
  mounted() {
    this.loadOrders()
  },
  methods: {
    loadOrders() {
      this.moLoading = true
      http.get('/mes/orders', { params: { pageNum: 1, pageSize: 20 } })
        .then(body => { this.orders = body.data.records || [] })
        .catch(showError).finally(() => { this.moLoading = false })
    },
    createMo() {
      http.post('/mes/orders', this.moForm).then(body => {
        this.$message.success('MO 已创建 ' + (body.data.moNo || body.data))
        this.moDialog = false
        this.loadOrders()
      }).catch(showError)
    },
    loadProcesses(row) {
      this.currentMo = row
      this.procLoading = true
      http.get(`/mes/orders/${row.id}/processes`).then(body => {
        this.processes = body.data || []
        this.tab = 'processes'
      }).catch(showError).finally(() => { this.procLoading = false })
    },
    start(row) {
      http.post(`/mes/processes/${row.id}/start`).then(() => {
        this.$message.success('工序已开工')
        this.loadProcesses(this.currentMo)
      }).catch(showError)
    },
    finish(row) {
      this.$prompt('合格量,不良量（如: 9,1）', '工序报工', { inputValue: '9,1' })
        .then(({ value }) => {
          const [goodQty, defectQty] = value.split(',').map(s => s.trim())
          return http.post(`/mes/processes/${row.id}/finish`, { goodQty, defectQty })
        })
        .then(() => { this.$message.success('报工完成'); this.loadProcesses(this.currentMo) })
        .catch(showError)
    },
    raiseAndon() {
      http.post('/mes/andon', this.andonForm).then(() => {
        this.$message.success('Andon 已拉响（分级升级由 job 推进）')
        this.andonDialog = false
      }).catch(showError)
    },
    materialCheck(row) {
      http.get(`/mes/orders/${row.id}/material-check`).then(body => {
        this.mcResult = body.data
        this.mcDialog = true
      }).catch(showError)
    },
    openQc(row) {
      this.qcTarget = row
      this.qcForm = { passed: true, rejectReason: '' }
      this.qcDialog = true
    },
    doQc() {
      if (this.qcForm.passed === false && !this.qcForm.rejectReason) {
        this.$message.warning('退回原因必填')
        return
      }
      http.post(`/mes/processes/${this.qcTarget.id}/qc`, {
        passed: this.qcForm.passed,
        rejectReason: this.qcForm.rejectReason
      }).then(() => {
        this.$message.success(this.qcForm.passed ? '已检验放行' : '已退回（工序重开工）')
        this.qcDialog = false
        this.loadProcesses(this.currentMo)
      }).catch(showError)
    }
  }
}
</script>
