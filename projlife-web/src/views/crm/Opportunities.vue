<template>
  <div class="pl-page">
    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="never">
          <div class="pl-toolbar">
            <el-input v-model="query.keyword" placeholder="商机名称" size="small" style="width: 200px" clearable @keyup.enter.native="load" />
            <el-select v-model="query.status" size="small" style="width: 120px" clearable placeholder="状态" @change="load">
              <el-option value="TRACKING" label="跟踪中" />
              <el-option value="WON" label="已成交" />
              <el-option value="LOST" label="已关闭" />
            </el-select>
            <el-button size="small" type="primary" icon="el-icon-search" @click="load">查询</el-button>
            <div class="spacer"></div>
            <el-button v-permission="'opportunity:create'" size="small" type="primary" icon="el-icon-plus" @click="openCreate">商机登记</el-button>
          </div>
  <pt-table :columns="columns" :data="records" :loading="loading" :total="total"
                :page.sync="page" aria-label="商机台账" empty-type="no-result"
                empty-label="商机" empty-action-text="新建商机" @empty-action="openCreate"
                @page-change="load" @row-click="openDetail">
          <template slot="cell-intentionLevel" slot-scope="{ value }">
            <el-tag size="mini" :type="value === 'H' ? 'danger' : value === 'M' ? 'warning' : 'info'">{{ value }}</el-tag>
          </template>
          <template slot="cell-status" slot-scope="{ value }">
            <pt-status-tag :value="value" :dict="statusDict" />
          </template>
          <template slot="actions" slot-scope="{ row }">
            <el-button size="mini" type="text" @click.stop="openFollowup(row)">跟进</el-button>
            <el-button v-if="row.status === 'TRACKING'" size="mini" type="text" class="opp-win"
                       v-permission="'opportunity:convert'" @click.stop="convert(row)">赢单转合同</el-button>
            <el-button v-if="row.status === 'TRACKING'" size="mini" type="text" @click.stop="lose(row)">丢单</el-button>
            <el-button v-if="row.status === 'LOST'" size="mini" type="text" @click.stop="reopen(row)">重激活</el-button>
          </template>
        </pt-table>
          <el-pagination background layout="total, prev, pager, next" :total="total"
                         :page-size="query.pageSize" :current-page.sync="query.pageNum" @current-change="load" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <!-- FR-CRM-13 -->
          <div slot="header"><b>销售漏斗</b></div>
          <div v-if="funnel">
            <p>跟踪中 <b>{{ funnel.tracking }}</b> 个 · 金额 {{ funnel.trackingAmount }}</p>
            <p>已成交 <b>{{ funnel.won }}</b> 个 · 金额 {{ funnel.wonAmount }}</p>
            <p>已关闭 {{ funnel.lost }} 个 · 赢单率 {{ (funnel.winRate * 100).toFixed(1) }}%</p>
          </div>
        </el-card>
        <el-card shadow="never" style="margin-top: 16px">
          <div slot="header"><b>跟进时间线{{ current ? '：' + current.oppName : '' }}</b></div>
          <div v-if="followups.length === 0" class="muted">选择商机后查看跟进记录</div>
          <el-timeline>
            <el-timeline-item v-for="f in followups" :key="f.id" :timestamp="f.createdAt">
              {{ f.followType }}：{{ f.content }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog title="商机登记（FR-CRM-01）" :visible.sync="dialog" width="520px">
      <el-form label-width="90px" size="small">
        <el-form-item label="商机名称" required><el-input v-model.trim="form.oppName" /></el-form-item>
        <el-form-item label="客户主体" required>
          <el-select v-model="form.customerId" filterable style="width: 100%" placeholder="从主体库选择">
            <el-option v-for="c in customers" :key="c.id" :value="c.id" :label="c.customerName" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model.trim="form.contact" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model.trim="form.phone" /></el-form-item>
        <el-form-item label="需求简述"><el-input v-model="form.summary" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="意向等级">
          <el-select v-model="form.intentionLevel" style="width: 100%">
            <el-option value="H" label="H 高" /><el-option value="M" label="M 中" /><el-option value="L" label="L 低" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计金额(元)"><el-input-number v-model="form.expectAmount" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="预计签约日"><el-date-picker v-model="form.expectSignDate" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
        <el-form-item label="竞争对手"><el-input v-model.trim="form.competitor" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="save">保存</el-button>
      </span>
    </el-dialog>

    <el-dialog title="跟进记录（FR-CRM-03）" :visible.sync="followDialog" width="480px">
      <el-form label-width="90px" size="small">
        <el-form-item label="类型">
          <el-select v-model="followForm.followType" style="width: 100%">
            <el-option v-for="t in ['拜访', '电话', '邮件', '报价']" :key="t" :value="t" :label="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required><el-input v-model="followForm.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="下一步"><el-input v-model.trim="followForm.nextAction" placeholder="下一步动作 + 日期" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="followDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveFollowup">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { showError } from '@/api/request'
import { PtTable, PtStatusTag } from '@/components/pt'

export default {
  name: 'OpportunitiesPage',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      columns: [
        { prop: 'oppNo', label: '编号', width: 150 },
        { prop: 'oppName', label: '商机名称', minWidth: 200 },
        { prop: 'intentionLevel', label: '意向', width: 70 },
        { prop: 'expectAmount', label: '预计金额(元)', type: 'money', align: 'right' },
        { prop: 'status', label: '状态', width: 96 },
        { prop: 'expectSignDate', label: '预计签约', width: 112 }
      ],
      statusDict: 'TRACKING:跟踪中,WON:已成交,LOST:已关闭',
      page: { pageNum: 1, pageSize: 20 },

      records: [], total: 0, loading: false,
      query: { pageNum: 1, pageSize: 20, keyword: '', status: '' },
      dialog: false, form: {},
      followDialog: false, followForm: {}, current: null, followups: [],
      funnel: null, customers: []
    }
  },
  mounted() {
    this.load()
    this.loadFunnel()
    this.loadCustomers()
  },
  methods: {
    statusLabel(status) {
      return { TRACKING: '跟踪中', WON: '已成交', LOST: '已关闭' }[status] || status
    },
    load() {
      this.loading = true
      this.$http.get('/crm/opportunities', { params: this.query }).then(body => {
        this.records = (body.data && body.data.records) || []
        this.total = (body.data && body.data.total) || 0
      }).catch(showError).finally(() => { this.loading = false })
    },
    loadFunnel() {
      this.$http.get('/crm/funnel').then(body => { this.funnel = body.data }).catch(() => {})
    },
    loadCustomers() {
      this.$http.get('/mdm/customers', { params: { pageNum: 1, pageSize: 100 } }).then(body => {
        this.customers = (body.data && body.data.records) || []
      }).catch(() => {})
    },
    openCreate() {
      this.form = { intentionLevel: 'M' }
      this.dialog = true
    },
    save() {
      if (!this.form.oppName || !this.form.customerId) {
        this.$message.warning('商机名称与客户主体必填')
        return
      }
      this.$http.post('/crm/opportunities', this.form).then(() => {
        this.$message.success('已登记（编号自动生成，状态跟踪中）')
        this.dialog = false
        this.load()
        this.loadFunnel()
      }).catch(showError)
    },
    openFollowup(row) {
      this.current = row
      this.followForm = { followType: '电话' }
      this.followDialog = true
      this.$http.get(`/crm/opportunities/${row.id}/followups`).then(body => {
        this.followups = body.data || []
      }).catch(() => {})
    },
    saveFollowup() {
      if (!this.followForm.content) {
        this.$message.warning('跟进内容必填')
        return
      }
      this.$http.post(`/crm/opportunities/${this.current.id}/followups`, this.followForm).then(() => {
        this.$message.success('已记录')
        this.$http.get(`/crm/opportunities/${this.current.id}/followups`).then(body => { this.followups = body.data || [] })
      }).catch(showError)
    },
    lose(row) {
      this.$prompt('丢单原因（价格过高/竞对优势/预算取消/其他+说明）', `丢单：${row.oppName}`, {
        inputPattern: /^.{4,}$/, inputErrorMessage: '原因至少 4 个字'
      }).then(({ value }) => {
        return this.$http.post(`/crm/opportunities/${row.id}/lose`, { lostReason: value })
      }).then(() => {
        this.$message.success('已关闭（商机不物理删除，BR-CRM-01）')
        this.load()
        this.loadFunnel()
      }).catch(e => { if (e !== 'cancel') showError(e) })
    },
    /** SPRINT-UID-R15：赢单转合同（FR-CRM-02 convert，TRACKING→WON 并关联合同）——修复"赢单后无入口"断链 */
    convert(row) {
      this.$prompt('输入已建档的合同 ID（合同台账列表首列）', `赢单转合同：${row.oppName}`, {
        inputPattern: /^\d+$/, inputErrorMessage: '请输入合同 ID（纯数字）',
        confirmButtonText: '确认赢单', cancelButtonText: '取消'
      }).then(({ value }) => {
        return this.$http.post(`/crm/opportunities/${row.id}/convert`, { contractId: Number(value) })
      }).then(() => {
        this.$message.success('已赢单并关联合同（漏斗转入合同阶段）')
        this.load()
        this.loadFunnel()
      }).catch(e => { if (e !== 'cancel') showError(e) })
    },
    reopen(row) {
      this.$http.post(`/crm/opportunities/${row.id}/reopen`).then(() => {
        this.$message.success('已重新激活为跟踪中')
        this.load()
        this.loadFunnel()
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.muted { color: var(--ink-4); font-size: 13px; }
.opp-win { color: var(--pri-500, #1F7B8C); font-weight: 600; }
.opp-win:hover { color: var(--pri-700, #155E6C); }
</style>
