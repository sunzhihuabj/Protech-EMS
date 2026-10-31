<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div class="pl-toolbar">
        <el-input v-model="query.keyword" placeholder="合同名称/编号" size="small" style="width: 220px" clearable @keyup.enter.native="load" />
        <el-select v-model="query.status" size="small" style="width: 130px" clearable placeholder="状态" @change="load">
          <el-option v-for="s in statuses" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-button size="small" type="primary" icon="el-icon-search" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button v-permission="'contract:create'" size="small" type="primary" icon="el-icon-plus" @click="openCreate">合同起草</el-button>
      </div>

      <pt-table :columns="columns" :data="records" :loading="loading" :total="total"
                :page.sync="page" aria-label="合同台账" empty-type="no-result"
                empty-label="合同" empty-action-text="新建合同" @empty-action="dialog = true"
                @page-change="load">
        <template slot="toolbar">
          <el-button v-permission="'contract:create'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">新建合同</el-button>
        </template>
        <template slot="cell-contractNo" slot-scope="{ value }">{{ value || '（未用印）' }}</template>
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status" :dict="statusDict" />
          <el-tag v-if="row.invoiceFrozen" size="mini" type="danger" style="margin-left: 4px">冻结</el-tag>
        </template>
        <template slot="actions" slot-scope="{ row }">
          <el-button v-if="row.status === 'DRAFT'" size="mini" type="text" @click.stop="submitReview(row)">提交评审</el-button>
          <el-button v-if="row.status === 'REVIEWED'" size="mini" type="text" @click.stop="seal(row)">用印登记</el-button>
          <el-button v-if="row.status === 'SIGNED'" size="mini" type="text" @click.stop="filing(row)">建档备案</el-button>
          <el-button size="mini" type="text" @click.stop="aiRisk(row)">AI 风险</el-button>
          <el-button size="mini" type="text" @click.stop="aiExtract(row)">AI 提取</el-button>
          <el-button v-if="row.contractNo" size="mini" type="text" @click.stop="change(row)">变更</el-button>
          <el-button v-if="row.status === 'PERFORMING'" size="mini" type="text" @click.stop="receipt(row)">录入收款</el-button>
        </template>
      </pt-table>
      <el-pagination background layout="total, prev, pager, next" :total="total"
                     :page-size="query.pageSize" :current-page.sync="query.pageNum" @current-change="load" />
    </el-card>

    <!-- 起草 -->
    <el-dialog title="合同起草（FR-CT-01）" :visible.sync="dialog" width="680px">
      <el-form label-width="110px" size="small">
        <el-form-item label="合同名称" required><el-input v-model.trim="form.contractName" placeholder="对方主体－项目/事项" /></el-form-item>
        <el-form-item label="性质（E-11）" required>
          <el-select v-model="form.nature" style="width: 100%" @change="form.businessType = ''">
            <el-option value="XS" label="XS 销售合同" />
            <el-option value="CG" label="CG 采购外包合同" />
            <el-option value="KJ" label="KJ 框架协议" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型（E-12）" required>
          <el-select v-model="form.businessType" style="width: 100%">
            <el-option v-for="b in businessByNature[form.nature] || allBusiness" :key="b.value" :value="b.value" :label="b.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="我方主体" required>
          <el-select v-model="form.legalEntityId" style="width: 100%">
            <el-option :value="1" label="PRT 特固兰（demo）" /><el-option :value="2" label="TLD 天立达（demo）" />
          </el-select>
        </el-form-item>
        <el-form-item label="对方主体" required>
          <el-select v-model="form.counterpartyId" filterable style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :value="c.id" :label="c.customerName" />
          </el-select>
        </el-form-item>
        <el-form-item label="甲方/乙方" required>
          <el-input v-model.trim="form.partyA" placeholder="甲方" style="margin-bottom: 6px" />
          <el-input v-model.trim="form.partyB" placeholder="乙方" />
        </el-form-item>
        <el-form-item label="金额/税率" required>
          <el-input-number v-model="form.amount" :min="0" style="width: 55%" />
          <el-input-number v-model="form.taxRate" :min="0" :max="1" :step="0.01" style="width: 40%" />
        </el-form-item>
        <el-form-item label="关联项目" required>
          <el-select v-model="form.projectIds" multiple style="width: 100%" placeholder="评审前至少 1 个（BR-01），下拉禁止手输">
            <el-option v-for="p in projects" :key="p.id" :value="p.id" :label="`${p.projectCode} ${p.stdName}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款计划" required>
          <div v-for="(plan, idx) in form.paymentPlans" :key="idx" style="display: flex; gap: 6px; margin-bottom: 6px">
            <el-input v-model.trim="plan.phaseName" placeholder="阶段名" style="width: 30%" />
            <el-input-number v-model="plan.percentage" :min="0" :max="1" :step="0.05" style="width: 34%" />
            <el-button size="mini" type="text" @click="form.paymentPlans.splice(idx, 1)">删除</el-button>
          </div>
          <el-button size="mini" @click="form.paymentPlans.push({ phaseName: '', percentage: 0.3 })">+ 阶段</el-button>
        </el-form-item>
        <el-form-item label="签订/生效日" required>
          <el-date-picker v-model="form.signDate" type="date" value-format="yyyy-MM-dd" placeholder="签订" style="width: 48%" />
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="yyyy-MM-dd" placeholder="生效" style="width: 48%" />
        </el-form-item>
        <el-form-item label="付款条件原文" required><el-input v-model="form.paymentTerms" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="质保(月)/交货期">
          <el-input-number v-model="form.warrantyMonths" :min="0" style="width: 45%" />
          <el-date-picker v-model="form.deliveryDate" type="date" value-format="yyyy-MM-dd" style="width: 50%" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="save">保存草稿</el-button>
      </span>
    </el-dialog>

    <!-- 用印 -->
    <el-dialog title="用印登记（FR-CT-17/18/19）" :visible.sync="sealDialog" width="420px">
      <p>将要生成的编号：<code>{{ numberPreview }}</code>（预览不占号）</p>
      <el-form label-width="100px" size="small">
        <el-form-item label="正本份数" required><el-input-number v-model="sealForm.copyCount" :min="1" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="sealDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doSeal">确认用印</el-button>
      </span>
    </el-dialog>

    <!-- AI 风险 -->
    <el-dialog title="AI 风险分析（AI-001，不阻塞归档）" :visible.sync="riskDialog" width="640px">
      <el-table :data="risks" size="mini">
        <el-table-column prop="riskLevel" label="等级" width="70">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="row.riskLevel === 'H' ? 'danger' : row.riskLevel === 'M' ? 'warning' : 'success'">
              {{ row.riskLevel === 'H' ? '红' : row.riskLevel === 'M' ? '黄' : '绿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskType" label="条款" width="110" />
        <el-table-column prop="riskDesc" label="风险描述" min-width="200" />
        <el-table-column prop="suggestion" label="建议" min-width="160" />
        <el-table-column label="操作" width="110">
          <template slot-scope="{ row }">
            <el-button size="mini" type="text" @click="adopt(row, true)">采纳</el-button>
            <el-button size="mini" type="text" @click="adopt(row, false)">忽略</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- AI 提取确认（左右分栏，FR-CT-08） -->
    <el-dialog title="AI 信息提取确认（AI-002）" :visible.sync="extractDialog" width="760px">
      <el-alert v-if="extractNote" :title="extractNote" type="warning" :closable="false" style="margin-bottom: 10px" />
      <el-row :gutter="16">
        <el-col :span="12">
          <h5>AI 提取值（左）</h5>
          <div v-for="(v, k) in aiFields" :key="k" class="extract-row">
            <span class="extract-key">{{ k }}</span><span>{{ v }}</span>
          </div>
        </el-col>
        <el-col :span="12">
          <h5>人工确认/修正（右）— 改动将记入 ai_feedback</h5>
          <div v-for="(v, k) in aiFields" :key="k" class="extract-row">
            <span class="extract-key">{{ k }}</span>
            <el-input v-model="humanFields[k]" size="mini" />
          </div>
        </el-col>
      </el-row>
      <span slot="footer">
        <el-button size="small" @click="extractDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="confirmExtract">确认归档</el-button>
      </span>
    </el-dialog>

    <!-- 变更 -->
    <el-dialog title="发起变更（FR-CT-31）" :visible.sync="changeDialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="变更类型">
          <el-select v-model="changeForm.changeType" style="width: 100%">
            <el-option value="AMOUNT" label="金额变更" /><el-option value="SCOPE" label="范围变更" />
            <el-option value="TERM" label="期限变更" /><el-option value="OTHER" label="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因" required><el-input v-model="changeForm.changeReason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="金额增减"><el-input-number v-model="changeForm.amountDelta" style="width: 100%" /></el-form-item>
        <el-form-item label="生效日期"><el-date-picker v-model="changeForm.effectiveDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="changeDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveChange">提交（生成 -BCn，R05 审批）</el-button>
      </span>
    </el-dialog>

    <!-- 收款 -->
    <el-dialog title="录入收款（FR-CT-36）" :visible.sync="receiptDialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="计划行">
          <el-select v-model="receiptForm.paymentPlanId" style="width: 100%">
            <el-option v-for="p in plans" :key="p.id" :value="p.id"
                       :label="`${p.phaseName} 应收 ${p.amount}（${p.status}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="回款金额" required><el-input-number v-model="receiptForm.amount" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="到账日期" required><el-date-picker v-model="receiptForm.receiptDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
        <el-form-item label="银行流水"><el-input v-model.trim="receiptForm.bankRef" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="receiptDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveReceipt">提交（财务确认后生效）</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { showError } from '@/api/request'
import { PtTable, PtStatusTag } from '@/components/pt'

export default {
  name: 'ContractsPage',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      columns: [
        { prop: 'contractNo', label: '合同编号', width: 190 },
        { prop: 'contractName', label: '名称', minWidth: 220 },
        { prop: 'nature', label: '性质', width: 70 },
        { prop: 'amount', label: '金额(元)', type: 'money', align: 'right' },
        { prop: 'status', label: '状态', width: 120 },
        { prop: 'signDate', label: '签订日', width: 112 }
      ],
      statusDict: 'DRAFT:编辑中,REVIEWED:已评审,SIGNED:已签署,PERFORMING:履行中,ACCEPTED:已验收,WARRANTY:质保期中,CLOSED:已关闭,CHANGED:已变更',
      page: { pageNum: 1, pageSize: 20 },
      records: [], total: 0, loading: false,
      query: { pageNum: 1, pageSize: 20, keyword: '', status: '' },
      statuses: [
        { value: 'DRAFT', label: '编辑中' }, { value: 'REVIEWED', label: '已评审' },
        { value: 'SIGNED', label: '已签署' }, { value: 'PERFORMING', label: '履行中' },
        { value: 'ACCEPTED', label: '已验收' }, { value: 'WARRANTY', label: '质保期中' },
        { value: 'CLOSED', label: '已关闭' }, { value: 'CHANGED', label: '已变更' }
      ],
      dialog: false, form: {},
      customers: [], projects: [],
      allBusiness: [
        { value: 'AZFW', label: 'AZFW 安装服务' }, { value: 'AZFWXF', label: 'AZFWXF 消防安装服务' },
        { value: 'DJFW', label: 'DJFW 定检服务' }, { value: 'DTAZFW', label: 'DTAZFW 升降机安装服务' },
        { value: 'SB', label: 'SB 设备供货' }, { value: 'BJ', label: 'BJ 备件' }, { value: 'JS', label: 'JS 技术服务' }
      ],
      businessByNature: {
        XS: [{ value: 'SB', label: 'SB 设备供货' }, { value: 'AZFW', label: 'AZFW 安装服务' },
             { value: 'AZFWXF', label: 'AZFWXF 消防安装服务' }, { value: 'DTAZFW', label: 'DTAZFW 升降机安装服务' },
             { value: 'BJ', label: 'BJ 备件' }, { value: 'DJFW', label: 'DJFW 定检服务' }, { value: 'JS', label: 'JS 技术服务' }],
        CG: [{ value: 'AZFW', label: 'AZFW 安装服务' }, { value: 'AZFWXF', label: 'AZFWXF 消防安装服务' },
             { value: 'DTAZFW', label: 'DTAZFW 升降机安装服务' }],
        KJ: [{ value: 'DJFW', label: 'DJFW 定检服务' }, { value: 'JS', label: 'JS 技术服务' }]
      },
      sealDialog: false, sealForm: {}, numberPreview: '', sealTarget: null,
      riskDialog: false, risks: [], riskTarget: null,
      extractDialog: false, aiFields: {}, humanFields: {}, extractNote: '', extractTarget: null,
      changeDialog: false, changeForm: {}, changeTarget: null,
      receiptDialog: false, receiptForm: {}, plans: [], receiptTarget: null
    }
  },
  mounted() {
    this.load()
    this.loadCustomers()
    this.loadProjects()
  },
  methods: {
    statusLabel(status) {
      const found = this.statuses.find(s => s.value === status)
      return found ? found.label : status
    },
    statusType(status) {
      return { SIGNED: 'success', PERFORMING: '', REVIEWED: 'warning', DRAFT: 'info', CLOSED: 'info' }[status] || ''
    },
    load() {
      this.loading = true
      this.$http.get('/contract', { params: this.query }).then(body => {
        this.records = (body.data && body.data.records) || []
        this.total = (body.data && body.data.total) || 0
      }).catch(showError).finally(() => { this.loading = false })
    },
    loadCustomers() {
      this.$http.get('/mdm/customers', { params: { pageNum: 1, pageSize: 100 } }).then(body => {
        this.customers = (body.data && body.data.records) || []
      }).catch(() => {})
    },
    loadProjects() {
      this.$http.get('/mdm/projects', { params: { pageNum: 1, pageSize: 100 } }).then(body => {
        this.projects = (body.data && body.data.records) || []
      }).catch(() => {})
    },
    openCreate() {
      this.form = { paymentPlans: [{ phaseName: '预付款', percentage: 0.3 }, { phaseName: '到货款', percentage: 0.4 }, { phaseName: '验收款', percentage: 0.3 }] }
      this.dialog = true
    },
    save() {
      if (!this.form.contractName || !this.form.nature || !this.form.businessType || !this.form.amount) {
        this.$message.warning('名称/性质/业务类型/金额必填')
        return
      }
      this.$http.post('/contract', this.form).then(() => {
        this.$message.success('草稿已保存')
        this.dialog = false
        this.load()
      }).catch(showError)
    },
    submitReview(row) {
      this.$confirm('提交评审前核对：六要素 + 已关联项目编码（BR-01 无项目编码将被拒绝）', '提交评审', { type: 'warning' })
        .then(() => this.$http.post(`/contract/${row.id}/review/submit`))
        .then(() => {
          this.$message.success('已提交评审（BR-01 通过）')
          this.load()
        }).catch(e => { if (e !== 'cancel') showError(e) })
    },
    seal(row) {
      this.sealTarget = row
      this.sealForm = { copyCount: 1 }
      this.numberPreview = '…'
      this.sealDialog = true
      this.$http.get(`/contract/${row.id}/number-preview`).then(body => { this.numberPreview = body.data }).catch(() => {})
    },
    doSeal() {
      this.$http.post(`/contract/${this.sealTarget.id}/seal`, this.sealForm).then(body => {
        this.$message.success(`已用印，合同号 ${body.data.contractNo}；建档时限 ${body.data.filingDeadline || '见详情'}`)
        this.sealDialog = false
        this.load()
      }).catch(showError)
    },
    filing(row) {
      this.$prompt('26 要素建档在详情页完成；此处演示完整要素一键备案（生产实现走建档表单）',
        `建档备案：${row.contractNo || row.contractName}`, {
          inputPlaceholder: '输入 Y 确认按示例要素备案',
          inputValidator: v => v === 'Y' || '输入 Y 继续'
        }).then(() => {
        // 演示：以当前合同已有要素 + 补齐项提交（生产实现为 26 要素表单）
        return this.$http.get(`/contract/${row.id}/completeness`)
      }).then(body => {
        this.$message.info(`当前完整度 ${(body.data.ratio * 100).toFixed(0)}%，缺失 ${(body.data.missing || []).length} 项`)
        this.load()
      }).catch(e => { if (e !== 'cancel') showError(e) })
    },
    aiRisk(row) {
      this.riskTarget = row
      this.$http.post(`/contract/${row.id}/ai/risk`, { text: row.contractName }).then(body => {
        this.$message.info(`分析完成（${body.data.isMock ? 'mock' : '真实模型'}，落库 ${body.data.savedRisks} 条）`)
        return this.$http.get(`/contract/${row.id}/ai/risks`)
      }).then(body => {
        this.risks = body.data || []
        this.riskDialog = true
      }).catch(showError)
    },
    adopt(row, adopted) {
      this.$http.post(`/contract/ai/risks/${row.id}/adopt`, { adopted }).then(() => {
        this.$message.success(adopted ? '已采纳' : '已忽略')
      }).catch(showError)
    },
    aiExtract(row) {
      this.extractTarget = row
      this.$http.post(`/contract/${row.id}/ai/extract`).then(body => {
        this.aiFields = body.data.fields || {}
        this.humanFields = { ...this.aiFields }
        this.extractNote = body.data.note || ''
        this.extractDialog = true
      }).catch(showError)
    },
    confirmExtract() {
      this.$http.post(`/contract/${this.extractTarget.id}/ai/confirm`, {
        aiFields: this.aiFields, humanFields: this.humanFields
      }).then(body => {
        this.$message.success(`确认归档完成，${body.data.correctedFields} 处人工修正已记入 ai_feedback（FR-CT-11）`)
        this.extractDialog = false
      }).catch(showError)
    },
    change(row) {
      this.changeTarget = row
      this.changeForm = { changeType: 'AMOUNT', amountDelta: 0, effectiveDate: null }
      this.changeDialog = true
    },
    saveChange() {
      if (!this.changeForm.changeReason || this.changeForm.changeReason.length < 4) {
        this.$message.warning('变更原因必填')
        return
      }
      this.$http.post(`/contract/${this.changeTarget.id}/changes`, this.changeForm).then(body => {
        this.$message.success(`变更单已提交（${body.data.changeNo || '编号见变更列表'}），待 R05 审批`)
        this.changeDialog = false
      }).catch(showError)
    },
    receipt(row) {
      this.receiptTarget = row
      this.receiptForm = { receiptDate: null }
      this.receiptDialog = true
      this.$http.get(`/contract/${row.id}/payment-plans`).then(body => {
        this.plans = body.data || []
      }).catch(() => {})
    },
    saveReceipt() {
      if (!this.receiptForm.amount || !this.receiptForm.receiptDate) {
        this.$message.warning('金额与到账日期必填')
        return
      }
      this.$http.post('/contract/receipts', { ...this.receiptForm, contractId: this.receiptTarget.id }).then(() => {
        this.$message.success('已提交，待财务确认（R15）')
        this.receiptDialog = false
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.extract-row { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
.extract-key { width: 130px; color: #8a97a3; font-size: 12px; flex-shrink: 0; }
</style>
