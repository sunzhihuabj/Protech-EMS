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
          <pt-status-tag :value="row.status" :dict="'TO_DISPATCH:待下发,DISPATCHED:已下发,DOING:进行中,TO_ACCEPT:待验收,DONE:已完工'" />
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

      <!-- SPRINT-UID-R20：班组人员准入（FR-ENG-01/02，UC-ENG-10 保险校验） -->
      <el-tab-pane label="班组人员" name="crew">
        <div class="pl-toolbar">
          <el-button v-permission="'eng:crew:manage'" size="small" type="primary" icon="el-icon-plus" @click="workerDialog = true">建人员档案</el-button>
          <el-select v-model="workerStatus" size="small" style="width: 130px" clearable placeholder="准入状态" @change="loadWorkers">
            <el-option value="PENDING" label="待激活" /><el-option value="ACTIVE" label="已激活" />
            <el-option value="FROZEN" label="已冻结" /><el-option value="EXPIRED" label="授权过期" />
          </el-select>
          <el-button size="small" icon="el-icon-refresh" @click="loadWorkers">刷新</el-button>
        </div>
        <pt-table :columns="workerCols" :data="workers" :loading="workerLoading" :total="0"
                  aria-label="班组人员台账" empty-type="no-data" empty-label="人员"
                  empty-action-text="建人员档案" @empty-action="workerDialog = true">
          <template slot="cell-status" slot-scope="{ row }">
            <pt-status-tag :value="row.status || 'PENDING'" :dict="'PENDING:待激活,ACTIVE:已激活,FROZEN:已冻结,EXPIRED:授权过期'" />
          </template>
          <template slot="cell-insuranceExpire" slot-scope="{ row }">
            <span :class="{ 'danger-text': isInsuranceExpired(row) }">{{ row.insuranceExpire || '未登记' }}</span>
          </template>
          <template slot="actions" slot-scope="{ row }">
            <el-button v-if="row.status === 'PENDING'" v-permission="'eng:crew:manage'" size="mini" type="text" @click.stop="activateWorker(row)">激活授权</el-button>
          </template>
        </pt-table>
        <p class="muted" style="margin: 6px 0 0">UC-ENG-10：保险过期激活将被拒绝（FROZEN/EXPIRED 亦不可激活）；身份证 AES 密文在 S9 安全批次接入。</p>
      </el-tab-pane>

      <!-- SPRINT-UID-R20：机组一机一档（E-29）+ 整改闭环（UC-ENG-08） -->
      <el-tab-pane label="机组与整改" name="units">
        <div class="pl-toolbar">
          <el-button v-permission="'eng:install'" size="small" type="primary" icon="el-icon-plus" @click="unitDialog = true">机组建档</el-button>
          <el-button v-permission="'eng:rect:manage'" size="small" type="warning" plain icon="el-icon-document-add" @click="rectDialog = true">登记整改</el-button>
          <span class="muted">项目ID</span>
          <el-input-number v-model="unitProjectId" :min="1" :controls="false" size="small" style="width: 90px" />
          <el-button size="small" v-permission="'eng:install'" icon="el-icon-circle-check" @click="rectOpenCheck">验收前检查</el-button>
        </div>
        <el-alert v-if="rectCheckResult !== null" :title="rectCheckResult" :type="rectCheckResult.indexOf('可提交') !== -1 ? 'success' : 'warning'"
                  :closable="true" show-icon style="margin-bottom: 8px" @close="rectCheckResult = null" />
        <el-table :data="units" v-loading="unitLoading" size="small">
          <el-table-column prop="unitNo" label="机组编号" width="170" />
          <el-table-column prop="unitName" label="机位" width="90" />
          <el-table-column prop="turbineModel" label="机型" width="110" />
          <el-table-column prop="turbineSn" label="序列号" width="140" />
          <el-table-column prop="locationDesc" label="安装位置" min-width="120" show-overflow-tooltip />
          <el-table-column prop="status" label="E-29 状态" width="110">
            <template slot-scope="{ row }">
              <pt-status-tag :value="row.status || 'TO_INSTALL'" :dict="'TO_INSTALL:待安装,INSTALLING:安装中,TO_DEBUG:待调试,DEBUGGING:调试中,ACCEPTED:已验收'" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template slot-scope="{ row }">
              <el-button v-if="nextUnitStatus(row.status)" v-permission="'eng:install'" size="mini" type="text" @click="advanceUnit(row)">推进至{{ unitStatusLabel(nextUnitStatus(row.status)) }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <h4 class="muted" style="margin: 14px 0 6px">整改单（PENDING → DOING → TO_REVIEW → PASSED → CLOSED；复核不通过回 DOING）</h4>
        <el-table :data="rects" v-loading="rectLoading" size="small" border>
          <el-table-column prop="rectNo" label="整改单号" width="150" />
          <el-table-column prop="problemDesc" label="问题描述" min-width="180" show-overflow-tooltip />
          <el-table-column prop="requirement" label="整改要求" min-width="140" show-overflow-tooltip />
          <el-table-column prop="deadline" label="期限" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template slot-scope="{ row }">
              <pt-status-tag :value="row.status || 'PENDING'" :dict="'PENDING:待整改,DOING:整改中,TO_REVIEW:待复核,PASSED:复核通过,CLOSED:已闭环'" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template slot-scope="{ row }">
              <el-button v-if="nextRectAction(row.status)" v-permission="'eng:rect:manage'" size="mini" type="text" @click="advanceRect(row)">{{ nextRectAction(row.status).label }}</el-button>
            </template>
          </el-table-column>
        </el-table>
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
    <!-- 人员档案弹窗（FR-ENG-01/02：W 编号自动取号，PENDING 待激活） -->
    <el-dialog title="建班组人员档案（W 编号自动取号）" :visible.sync="workerDialog" width="480px">
      <el-form label-width="110px" size="small">
        <el-form-item label="姓名" required><el-input v-model.trim="workerForm.name" /></el-form-item>
        <el-form-item label="班组ID" required><el-input-number v-model="workerForm.crewId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model.trim="workerForm.phone" /></el-form-item>
        <el-form-item label="保险到期日">
          <el-date-picker v-model="workerForm.insuranceExpire" type="date" value-format="yyyy-MM-dd" style="width: 100%" placeholder="UC-ENG-10 激活前置校验" />
        </el-form-item>
        <el-form-item label="证书(JSON)"><el-input v-model="workerForm.certificatesJson" type="textarea" :rows="2" placeholder='如 [{"type":"电工证","no":"T12345"}]' /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="workerDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createWorker">建档</el-button>
      </div>
    </el-dialog>

    <!-- 机组建档弹窗（FR-ENG-21：U-P{项目}-{3位}，productLines 必填） -->
    <el-dialog title="机组建档（一机一档 U-P{项目}-{3位}）" :visible.sync="unitDialog" width="500px">
      <el-form label-width="110px" size="small">
        <el-form-item label="项目档案ID" required><el-input-number v-model="unitForm.mdProjectId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="机位名" required><el-input v-model.trim="unitForm.unitName" placeholder="如 A01" /></el-form-item>
        <el-form-item label="机型"><el-input v-model.trim="unitForm.turbineModel" /></el-form-item>
        <el-form-item label="机组序列号"><el-input v-model.trim="unitForm.turbineSn" /></el-form-item>
        <el-form-item label="产品线" required>
          <el-select v-model="unitForm.productLine" style="width: 100%" placeholder="E-08 单值（追溯依据）">
            <el-option value="LADDER" label="升降机 LADDER" /><el-option value="CLIMBER" label="免爬器 CLIMBER" />
            <el-option value="CLIMB" label="爬梯 CLIMB" /><el-option value="RAIL" label="导轨 RAIL" />
            <el-option value="CCTV" label="视频监控 CCTV" /><el-option value="FIRE" label="自动消防 FIRE" />
            <el-option value="COVER" label="自动盖板 COVER" /><el-option value="ROPE" label="钢丝绳 ROPE" />
            <el-option value="AUDIO" label="声频 AUDIO" /><el-option value="SENSOR" label="感温磁 SENSOR" />
            <el-option value="OTHER" label="其他 OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="安装位置"><el-input v-model.trim="unitForm.locationDesc" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="unitDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createUnit">建档</el-button>
      </div>
    </el-dialog>

    <!-- 整改登记弹窗（FR-ENG-14：三来源 RECT- 编号，超期升级 UC-ENG-08） -->
    <el-dialog title="登记整改（RECT 编号自动取号）" :visible.sync="rectDialog" width="520px">
      <el-form label-width="100px" size="small">
        <el-form-item label="项目ID" required><el-input-number v-model="rectForm.projectId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="机组ID"><el-input-number v-model="rectForm.unitId" :min="1" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="问题描述" required><el-input v-model="rectForm.problemDesc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="整改要求"><el-input v-model="rectForm.requirement" type="textarea" :rows="2" placeholder="验收标准/复检要求" /></el-form-item>
        <el-form-item label="整改期限"><el-date-picker v-model="rectForm.deadline" type="date" value-format="yyyy-MM-dd" style="width: 100%" placeholder="超期升级（UC-ENG-08）" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="rectDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createRect">登记</el-button>
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
      woList: [], woLoading: false, woDialog: false, woForm: { urgency: 'NORMAL', warrantyFlag: 0 },
      // SPRINT-UID-R20：班组人员准入
      workerCols: [
        { prop: 'workerCode', label: '工号', width: 100 },
        { prop: 'name', label: '姓名', width: 100 },
        { prop: 'crewId', label: '班组', width: 70 },
        { prop: 'phone', label: '手机号', width: 130 },
        { prop: 'insuranceExpire', label: '保险到期', width: 110 },
        { prop: 'status', label: '准入状态', width: 100 }
      ],
      workers: [], workerLoading: false, workerStatus: '', workerDialog: false, workerForm: {},
      // SPRINT-UID-R20：机组一机一档 + 整改闭环
      unitDialog: false, unitForm: {},
      units: [], unitLoading: false, unitProjectId: null,
      rectDialog: false, rectForm: {},
      rects: [], rectLoading: false, rectCheckResult: null
    }
  },
  watch: {
    // 项目ID变化联动机组/整改列表过滤（R20）
    unitProjectId() { this.loadUnits(); this.loadRects() }
  },
  mounted() {
    this.loadDispatch()
    this.loadWo()
    this.loadWorkers()
    this.loadUnits()
    this.loadRects()
  },
  methods: {
    nextActions(status) {
      // 对齐后端 E-32：TO_DISPATCH→DISPATCHED→DOING→TO_ACCEPT→DONE
      return {
        TO_DISPATCH: ['send'], DISPATCHED: ['start'], DOING: ['accept'], TO_ACCEPT: ['close']
      }[status] || []
    },
    actionLabel(a) {
      return { send: '下发', start: '开工', accept: '提请验收', close: '完工' }[a] || a
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
    },

    /* ---------- SPRINT-UID-R20：班组人员准入（FR-ENG-01/02，UC-ENG-10） ---------- */
    isInsuranceExpired(row) {
      return row.insuranceExpire && row.insuranceExpire < new Date().toISOString().slice(0, 10)
    },
    loadWorkers() {
      this.workerLoading = true
      const params = { pageNum: 1, pageSize: 50 }
      if (this.workerStatus) params.status = this.workerStatus
      http.get('/eng/workers', { params }).then(body => {
        this.workers = (body.data && body.data.records) || []
      }).catch(showError).finally(() => { this.workerLoading = false })
    },
    createWorker() {
      if (!this.workerForm.name || !this.workerForm.crewId) {
        this.$message.warning('姓名与班组必填')
        return
      }
      http.post('/eng/workers', this.workerForm).then(body => {
        this.$message.success('人员已建档 ' + (typeof body.data === 'object' ? body.data.workerCode : body.data) + '（PENDING，待激活）')
        this.workerDialog = false
        this.workerForm = {}
        this.loadWorkers()
      }).catch(showError)
    },
    activateWorker(row) {
      const tip = this.isInsuranceExpired(row)
        ? `保险已于 ${row.insuranceExpire} 过期，激活将被 UC-ENG-10 拒绝`
        : `激活 ${row.name}（${row.workerCode}）的施工授权？`
      this.$confirm(tip, '人员激活授权', { type: this.isInsuranceExpired(row) ? 'warning' : 'info' }).then(() => {
        http.post(`/eng/workers/${row.id}/activate`).then(() => {
          this.$message.success('已激活（ACTIVE，可参与派工）')
          this.loadWorkers()
        }).catch(showError)
      }).catch(() => {})
    },

    /* ---------- SPRINT-UID-R20：机组一机一档（E-29）+ 整改闭环（UC-ENG-08） ---------- */
    loadUnits() {
      this.unitLoading = true
      const params = { pageNum: 1, pageSize: 50 }
      if (this.unitProjectId) params.projectId = this.unitProjectId
      http.get('/eng/units', { params }).then(body => {
        this.units = (body.data && body.data.records) || []
      }).catch(showError).finally(() => { this.unitLoading = false })
    },
    createUnit() {
      if (!this.unitForm.mdProjectId || !this.unitForm.unitName || !this.unitForm.productLine) {
        this.$message.warning('项目档案/机位名/产品线必填')
        return
      }
      http.post('/eng/units', { ...this.unitForm, productLines: this.unitForm.productLine }).then(body => {
        this.$message.success('机组已建档 ' + (typeof body.data === 'object' ? body.data.unitNo : body.data))
        this.unitDialog = false
        this.unitForm = {}
        this.loadUnits()
      }).catch(showError)
    },
    nextUnitStatus(status) {
      // 对齐后端 E-29 合法迁移表
      return { TO_INSTALL: 'INSTALLING', INSTALLING: 'TO_DEBUG', TO_DEBUG: 'ACCEPTED', DEBUGGING: 'ACCEPTED' }[status] || null
    },
    unitStatusLabel(s) {
      return { INSTALLING: '安装中', TO_DEBUG: '待调试', ACCEPTED: '已验收' }[s] || s
    },
    advanceUnit(row) {
      const target = this.nextUnitStatus(row.status)
      this.$confirm(`机组 ${row.unitNo} 推进至 ${this.unitStatusLabel(target)}？验收将自动起算 24 个月质保。`, 'E-29 状态推进').then(() => {
        http.post(`/eng/units/${row.id}/advance`, { targetStatus: target }).then(() => {
          this.$message.success('已推进至 ' + this.unitStatusLabel(target))
          this.loadUnits()
        }).catch(showError)
      }).catch(() => {})
    },
    loadRects() {
      this.rectLoading = true
      const params = { pageNum: 1, pageSize: 50 }
      if (this.unitProjectId) params.projectId = this.unitProjectId
      http.get('/eng/rectifications', { params }).then(body => {
        this.rects = (body.data && body.data.records) || []
      }).catch(showError).finally(() => { this.rectLoading = false })
    },
    createRect() {
      if (!this.rectForm.projectId || !this.rectForm.problemDesc) {
        this.$message.warning('项目与问题描述必填（FR-ENG-14）')
        return
      }
      http.post('/eng/rectifications', { ...this.rectForm, sourceType: 'MANUAL' }).then(body => {
        this.$message.success('整改单 ' + (typeof body.data === 'object' ? body.data.rectNo : body.data) + ' 已登记')
        this.rectDialog = false
        this.rectForm = {}
        this.loadRects()
      }).catch(showError)
    },
    nextRectAction(status) {
      // 对齐后端整改状态机：PENDING→DOING→TO_REVIEW→PASSED→CLOSED；TO_REVIEW 可回 DOING
      return {
        PENDING: { target: 'DOING', label: '开始整改' },
        DOING: { target: 'TO_REVIEW', label: '提请复核' },
        TO_REVIEW: { target: 'PASSED', label: '复核通过' },
        PASSED: { target: 'CLOSED', label: '闭环归档' }
      }[status] || null
    },
    advanceRect(row) {
      const act = this.nextRectAction(row.status)
      if (row.status === 'TO_REVIEW') {
        // 复核：通过或回退（回退必填原因）
        this.$confirm(`复核 ${row.rectNo}：通过进入 PASSED，或驳回回 DOING？`, '整改复核', {
          distinguishCancelAndClose: true,
          confirmButtonText: '复核通过',
          cancelButtonText: '驳回回退'
        }).then(() => {
          return http.post(`/eng/rectifications/${row.id}/advance`, { targetStatus: 'PASSED' })
            .then(() => { this.$message.success('复核通过'); this.loadRects() })
        }).catch(action => {
          if (action !== 'cancel') return
          return this.$prompt('驳回原因（必填，将回退整改中）', '复核驳回')
            .then(({ value }) => http.post(`/eng/rectifications/${row.id}/advance`, { targetStatus: 'DOING', rejectReason: value }))
            .then(() => { this.$message.success('已驳回回 DOING'); this.loadRects() })
            .catch(err => { if (err !== 'cancel') showError(err) })
        }).catch(showError)
        return
      }
      http.post(`/eng/rectifications/${row.id}/advance`, { targetStatus: act.target }).then(() => {
        this.$message.success(act.label + '完成')
        this.loadRects()
      }).catch(showError)
    },
    rectOpenCheck() {
      if (!this.unitProjectId) { this.$message.warning('输入项目ID'); return }
      http.get(`/eng/projects/${this.unitProjectId}/rect-open-check`).then(body => {
        this.rectCheckResult = body.data || '无未闭环整改，可提交整体验收'
        this.$message.success('检查通过：无未闭环整改')
      }).catch(err => {
        // 400 = 存在未闭环整改（FR-ENG-08 前置校验）
        const resp = err && err.response
        if (resp && resp.data && resp.data.message) {
          this.rectCheckResult = resp.data.message
        } else {
          showError(err)
        }
      })
    }
  }
}
</script>

<style scoped>
.danger-text { color: #f56c6c; }
.muted { color: var(--ink-3); font-size: 12px; }
</style>
