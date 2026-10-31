<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <!-- 派工 -->
      <el-tab-pane label="派工" name="dispatch">
        <div class="pl-toolbar">
          <el-button v-permission="'eng:dispatch'" size="small" type="primary" icon="el-icon-plus" @click="dpDialog = true">新建派工（BR-03/04）</el-button>
          <el-button size="small" icon="el-icon-refresh" @click="loadDispatch">刷新</el-button>
        </div>
        <pt-table :columns="dpCols" :data="dispatchList" :loading="dpLoading"
                  :total="0" aria-label="派工单台账" empty-type="no-data"
                  empty-label="派工单" empty-action-text="新建派工" @empty-action="dpDialog = true">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status" :dict="'CREATED:创建,SENT:已下发,ACCEPTED:已接单,STARTED:已开工,CLOSED:已完工'" />
        </template>
        <template slot="actions" slot-scope="{ row }">
          <el-button v-for="a in nextActions(row.status)" :key="a" v-permission="'eng:dispatch'" size="mini" type="text" @click.stop="advance(row, a)">{{ actionLabel(a) }}</el-button>
        </template>
      </pt-table>
      </el-tab-pane>

      <!-- 施工日志 / 质检 / 物料 -->
      <el-tab-pane label="日常记录" name="records">
        <div class="pl-toolbar">
          <el-button v-permission="'eng:log'" size="small" type="primary" icon="el-icon-edit-outline" @click="logDialog = true">记施工日志</el-button>
          <el-button v-permission="'eng:install'" size="small" @click="qcDialog = true">质量检查</el-button>
          <el-button v-permission="'eng:expense'" size="small" @click="issueDialog = true">现场领料</el-button>
        </div>
        <el-empty description="记录提交后经 AI-009 合规检查（降级不阻塞），可在售后/整改模块跟踪闭环" :image-size="70" />
      </el-tab-pane>

      <!-- 售后 -->
      <el-tab-pane label="售后工单" name="after">
        <div class="pl-toolbar">
          <el-button v-permission="'eng:after:manage'" size="small" type="primary" icon="el-icon-bell" @click="woDialog = true">报修登记</el-button>
          <el-button size="small" icon="el-icon-refresh" @click="loadWo">刷新</el-button>
        </div>
        <pt-table :columns="woCols" :data="woList" :loading="woLoading"
                  :total="0" aria-label="售后工单台账" empty-type="no-data"
                  empty-label="售后工单" empty-action-text="报修登记" @empty-action="woDialog = true">
        <template slot="cell-urgency" slot-scope="{ row }">
          <pt-status-tag :value="row.urgency" :dict="'HIGH:高,NORMAL:中,LOW:低'" />
        </template>
        <template slot="cell-warrantyFlag" slot-scope="{ row }">{{ row.warrantyFlag === 1 ? '是' : '否' }}</template>
        <template slot="actions" slot-scope="{ row }">
          <el-button v-if="row.status === 'TO_DISPATCH'" v-permission="'eng:after:manage'" size="mini" type="text" @click.stop="respondWo(row)">响应</el-button>
          <el-button v-if="row.status === 'RESPONDED'" v-permission="'eng:after:manage'" size="mini" type="text" @click.stop="closeWo(row)">闭环</el-button>
        </template>
      </pt-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 派工弹窗 -->
    <el-dialog title="新建派工（缺项目 BR-03 / 缺风场 BR-04）" :visible.sync="dpDialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="项目ID" required><el-input v-model.trim="dpForm.mdProjectId" /></el-form-item>
        <el-form-item label="风场ID" required><el-input v-model.trim="dpForm.farmId" /></el-form-item>
        <el-form-item label="负责人ID" required><el-input v-model.trim="dpForm.leaderId" /></el-form-item>
        <el-form-item label="计划开始" required><el-date-picker v-model="dpForm.planStart" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
        <el-form-item label="计划结束" required><el-date-picker v-model="dpForm.planEnd" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dpDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createDispatch">创建</el-button>
      </div>
    </el-dialog>

    <!-- 日志弹窗 -->
    <el-dialog title="记施工日志（AI-009 合规检查，降级不阻塞）" :visible.sync="logDialog" width="500px">
      <el-form label-width="100px" size="small">
        <el-form-item label="派工单ID" required><el-input v-model.trim="logForm.dispatchId" /></el-form-item>
        <el-form-item label="项目ID" required><el-input v-model.trim="logForm.projectId" /></el-form-item>
        <el-form-item label="人员ID" required><el-input v-model.trim="logForm.workerId" /></el-form-item>
        <el-form-item label="天气"><el-input v-model.trim="logForm.weather" placeholder="SUNNY/RAINY/..." /></el-form-item>
        <el-form-item label="内容" required><el-input v-model="logForm.content" type="textarea" :rows="3" placeholder="≥5 字" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="logDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveLog">保存</el-button>
      </div>
    </el-dialog>

    <!-- 质检弹窗 -->
    <el-dialog title="质量检查（FAIL 必填缺陷描述）" :visible.sync="qcDialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="机组ID" required><el-input v-model.trim="qcForm.unitId" /></el-form-item>
        <el-form-item label="检查项" required><el-input v-model.trim="qcForm.checkItem" /></el-form-item>
        <el-form-item label="结果" required>
          <el-radio-group v-model="qcForm.result">
            <el-radio label="PASS">合格</el-radio><el-radio label="FAIL">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="qcForm.result === 'FAIL'" label="缺陷描述" required>
          <el-input v-model="qcForm.defectDesc" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="qcDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveQc">提交</el-button>
      </div>
    </el-dialog>

    <!-- 领料弹窗 -->
    <el-dialog title="现场领料（退料累计不超领料）" :visible.sync="issueDialog" width="460px">
      <el-form label-width="100px" size="small">
        <el-form-item label="项目ID" required><el-input v-model.trim="issueForm.projectId" /></el-form-item>
        <el-form-item label="物料ID" required><el-input v-model.trim="issueForm.materialId" /></el-form-item>
        <el-form-item label="数量" required><el-input v-model.trim="issueForm.issueQty" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="issueDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveIssue">领用</el-button>
      </div>
    </el-dialog>

    <!-- 报修弹窗 -->
    <el-dialog title="售后报修（WO 编号自动生成）" :visible.sync="woDialog" width="460px">
      <el-form label-width="100px" size="small">
        <el-form-item label="项目ID" required><el-input v-model.trim="woForm.mdProjectId" /></el-form-item>
        <el-form-item label="故障类型"><el-input v-model.trim="woForm.faultType" placeholder="PITCH_FAULT/YAW_FAULT/..." /></el-form-item>
        <el-form-item label="紧急度">
          <el-select v-model="woForm.urgency" style="width: 100%">
            <el-option value="HIGH" label="高" /><el-option value="NORMAL" label="中" /><el-option value="LOW" label="低" />
          </el-select>
        </el-form-item>
        <el-form-item label="保内">
          <el-switch v-model="woForm.warrantyFlag" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="woDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createWo">登记</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'
import { PtTable, PtStatusTag } from '@/components/pt'

export default {
  name: 'EngPortal',
  components: { PtTable, PtStatusTag },
  components: { PtTable, PtStatusTag },
  data() {
    return {
woCols: [
        { prop: 'orderNo', label: '工单号', width: 160 },
        { prop: 'faultType', label: '故障类型', width: 130 },
        { prop: 'urgency', label: '紧急度', width: 90 },
        { prop: 'warrantyFlag', label: '保内', width: 70 },
        { prop: 'status', label: '状态', width: 120 },
        { prop: 'solution', label: '方案', minWidth: 180 }
      ],
dpCols: [
        { prop: 'dispatchNo', label: '派工单', width: 150 },
        { prop: 'mdProjectId', label: '项目', width: 80 },
        { prop: 'farmId', label: '风场', width: 80 },
        { prop: 'leaderId', label: '负责人', width: 80 },
        { prop: 'planStart', label: '计划开始', width: 112 },
        { prop: 'planEnd', label: '计划结束', width: 112 },
        { prop: 'status', label: '状态', width: 120 }
      ],
      tab: 'dispatch',
      dispatchList: [], dpLoading: false, dpDialog: false, dpForm: {},
      logDialog: false, logForm: {},
      qcDialog: false, qcForm: { result: 'PASS' },
      issueDialog: false, issueForm: {},
      woList: [], woLoading: false, woDialog: false, woForm: { urgency: 'NORMAL', warrantyFlag: 0 }
    }
  },
  mounted() {
    this.loadDispatch()
    this.loadWo()
  },
  methods: {
    nextActions(status) {
      return {
        CREATED: ['send'], SENT: ['accept'], ACCEPTED: ['start'], STARTED: ['close']
      }[status] || []
    },
    actionLabel(a) {
      return { send: '下发', accept: '接单', start: '开工', close: '完工' }[a] || a
    },
    loadDispatch() {
      this.dpLoading = true
      http.get('/eng/dispatch', { params: { pageNum: 1, pageSize: 20 } })
        .then(body => { this.dispatchList = body.data.records || [] })
        .catch(showError).finally(() => { this.dpLoading = false })
    },
    createDispatch() {
      http.post('/eng/dispatch', this.dpForm).then(body => {
        this.$message.success('派工单已创建 ' + (body.data.dispatchNo || body.data))
        this.dpDialog = false
        this.loadDispatch()
      }).catch(showError)
    },
    advance(row, action) {
      http.post(`/eng/dispatch/${row.id}/${action}`).then(() => {
        this.$message.success('已' + this.actionLabel(action))
        this.loadDispatch()
      }).catch(showError)
    },
    saveLog() {
      http.post('/eng/records/daily-logs', this.logForm).then(body => {
        this.$notify({ title: '日志已保存（AI-009）', message: 'AI 检查结果已随日志落库（降级不阻塞）', type: 'success' })
        this.logDialog = false
      }).catch(showError)
    },
    saveQc() {
      http.post('/eng/records/quality-checks', this.qcForm).then(() => {
        this.$message.success('质检已记录' + (this.qcForm.result === 'FAIL' ? '，请跟进整改闭环' : ''))
        this.qcDialog = false
      }).catch(showError)
    },
    saveIssue() {
      http.post('/eng/records/material-issues', this.issueForm).then(body => {
        this.$message.success('领料已登记 id=' + body.data)
        this.issueDialog = false
      }).catch(showError)
    },
    loadWo() {
      this.woLoading = true
      http.get('/eng/records/after-sales').then(body => {
        this.woList = body.data || []
      }).catch(showError).finally(() => { this.woLoading = false })
    },
    createWo() {
      http.post('/eng/records/after-sales', this.woForm).then(body => {
        this.$message.success('工单 ' + body.data.orderNo + ' 已登记')
        this.woDialog = false
        this.loadWo()
      }).catch(showError)
    },
    respondWo(row) {
      http.post(`/eng/records/after-sales/${row.id}/respond`, {}).then(() => {
        this.$message.success('已响应')
        this.loadWo()
      }).catch(showError)
    },
    closeWo(row) {
      this.$prompt('处理方案（≥5 字），格式：方案,费用', '售后闭环', { inputValue: '更换部件并测试通过,3200' })
        .then(({ value }) => {
          const [solution, costAmount] = value.split(',').map(s => s.trim())
          return http.post(`/eng/records/after-sales/${row.id}/close`, { solution, costAmount })
        })
        .then(() => { this.$message.success('已闭环（客户确认置位）'); this.loadWo() })
        .catch(showError)
    }
  }
}
</script>
