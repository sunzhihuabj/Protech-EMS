<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <pt-filter-bar :fields="filterFields" :model.sync="query" collapsed @search="onSearch" @reset="onSearch" />

      <pt-table :columns="columns" :data="records" :loading="loading" :total="total"
                :page.sync="page" aria-label="报价单台账" empty-type="no-result"
                empty-label="报价单" empty-hint="调整筛选条件，或新建第一条报价单"
                empty-action-text="新建报价单" @empty-action="openCreate"
                @page-change="load" @row-click="openLines">
        <template slot="toolbar">
          <el-button v-permission="'opportunity:create'" size="small" type="primary" icon="el-icon-plus" @click="openCreate">新建报价单</el-button>
          <el-button size="small" icon="el-icon-data-line" @click="loadConversion">来源转化统计</el-button>
        </template>
        <template slot="cell-status" slot-scope="{ value }">
          <pt-status-tag :value="value" :dict="statusDict" />
        </template>
      </pt-table>
    </el-card>

    <!-- 明细抽屉 -->
    <el-drawer :visible.sync="drawer" size="55%" :title="detail ? detail.quotationNo + '（V' + detail.version + '）' : '报价单明细'">
      <div style="padding: 0 16px">
        <div class="pl-toolbar">
          <el-button v-permission="'opportunity:edit'" size="mini" type="success" :disabled="!detail || detail.status !== 'DRAFT'" @click="submit">提交评审</el-button>
          <el-button v-permission="'opportunity:edit'" size="mini" type="warning" :disabled="!detail || detail.status !== 'SUBMITTED'" @click="decide('WON')">定标（转合同 FR-CRM-19）</el-button>
          <el-button v-permission="'opportunity:edit'" size="mini" :disabled="!detail || detail.status !== 'SUBMITTED'" @click="decide('LOST')">未中标</el-button>
          <el-button size="mini" @click="showPriceBasis">价格基准（FR-CRM-18）</el-button>
        </div>
        <el-table :data="lines" size="mini">
          <el-table-column prop="lineNo" label="#" width="50" />
          <el-table-column prop="materialId" label="物料" width="90" />
          <el-table-column prop="qty" label="数量" width="90" />
          <el-table-column prop="price" label="单价" width="110" />
          <el-table-column prop="amount" label="行金额" width="120" />
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="lines.length === 0" description="暂无明细" :image-size="60" />
      </div>
    </el-drawer>

    <!-- 新建：主行 + 行集 -->
    <el-dialog title="新建报价单（金额=数量×单价×(1+税率) 服务端计算）" :visible.sync="dialog" width="640px">
      <el-form label-width="90px" size="small">
        <el-form-item label="客户主体" required>
          <el-select v-model="form.customerId" filterable style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :value="c.id" :label="c.customerName" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品线">
          <el-select v-model="form.productLine" style="width: 100%">
            <el-option value="LADDER" label="梯次 LADDER" /><el-option value="TOWER" label="塔筒 TOWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="交货条款"><el-input v-model.trim="form.deliveryTerms" placeholder="如 FOB 天津" /></el-form-item>
        <el-form-item label="有效期至"><el-date-picker v-model="form.validUntil" type="date" value-format="yyyy-MM-dd" style="width: 100%" /></el-form-item>
        <el-form-item label="明细行" required>
          <el-table :data="formLines" size="mini">
            <el-table-column width="90"><template slot-scope="{ row }"><el-input v-model.trim="row.materialId" size="mini" placeholder="物料ID" /></template></el-table-column>
            <el-table-column width="90"><template slot-scope="{ row }"><el-input v-model.trim="row.qty" size="mini" placeholder="数量" /></template></el-table-column>
            <el-table-column width="110"><template slot-scope="{ row }"><el-input v-model.trim="row.price" size="mini" placeholder="单价" /></template></el-table-column>
            <el-table-column><template slot-scope="{ row }"><el-input v-model.trim="row.remark" size="mini" placeholder="备注" /></template></el-table-column>
            <el-table-column width="50"><template slot-scope="{ $index }"><el-button type="text" size="mini" icon="el-icon-delete" @click="formLines.splice($index, 1)" /></template></el-table-column>
          </el-table>
          <el-button type="text" size="mini" icon="el-icon-plus" @click="formLines.push({})">加行</el-button>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">创建</el-button>
      </div>
    </el-dialog>

    <el-dialog title="来源转化统计（FR-CRM-20）" :visible.sync="convDialog" width="460px">
      <pre style="white-space: pre-wrap; font-size: 12px">{{ convText }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'
import { PtFilterBar, PtTable, PtStatusTag } from '@/components/pt'

export default {
  name: 'Quotations',
  data() {
    return {
      filterFields: [
        { prop: 'keyword', label: '报价单号' },
        { prop: 'status', label: '状态', type: 'select', options: [
          { value: 'DRAFT', label: '草稿' }, { value: 'SUBMITTED', label: '已提交' },
          { value: 'WON', label: '已定标' }, { value: 'LOST', label: '未中标' }] }
      ],
      columns: [
        { prop: 'quotationNo', label: '报价单号', width: 180 },
        { prop: 'version', label: '版本', width: 70 },
        { prop: 'amount', label: '金额(元)', type: 'money', align: 'right' },
        { prop: 'status', label: '状态', width: 100 },
        { prop: 'sourceType', label: '来源', width: 90 },
        { prop: 'deliveryTerms', label: '交货条款', width: 110 },
        { prop: 'validUntil', label: '有效期至', width: 112 },
        { prop: 'createdAt', label: '创建时间', minWidth: 150 }
      ],
      statusDict: 'DRAFT:草稿,SUBMITTED:已提交,WON:已定标,LOST:未中标',
      query: { pageNum: 1, pageSize: 20, keyword: '', status: '' },
      page: { pageNum: 1, pageSize: 20 },
      records: [], total: 0, loading: false,
      drawer: false, detail: null, lines: [],
      dialog: false, form: {}, formLines: [{}],
      customers: [],
      convDialog: false, convText: ''
    }
  },
  mounted() {
    this.load()
    http.get('/mdm/customers', { params: { pageNum: 1, pageSize: 100 } })
      .then(body => { this.customers = (body.data && body.data.records) || [] }).catch(() => {})
  },
  methods: {
    statusLabel(s) {
      return { DRAFT: '草稿', SUBMITTED: '已提交', WON: '已定标', LOST: '未中标' }[s] || s
    },
    load() {
      this.loading = true
      this.query.pageNum = this.page.pageNum
      this.query.pageSize = this.page.pageSize
      http.get('/crm/quotations', { params: this.query }).then(body => {
        this.records = body.data.records || []
        this.total = body.data.total || 0
        // URL 即状态（UID-02 §五）：筛选与分页写入 query，刷新后重建
        this.$router.replace({ query: { keyword: this.query.keyword || undefined, status: this.query.status || undefined, pageNum: this.page.pageNum > 1 ? this.page.pageNum : undefined } }).catch(() => {})
      }).catch(showError).finally(() => { this.loading = false })
    },
    onSearch() { this.page.pageNum = 1; this.load() },
    openLines(row) {
      this.detail = row
      this.drawer = true
      http.get(`/crm/quotations/${row.id}/lines`).then(body => { this.lines = body.data || [] }).catch(showError)
    },
    openCreate() {
      this.form = {}
      this.formLines = [{}]
      this.dialog = true
    },
    create() {
      const lines = this.formLines
        .filter(l => l.materialId && l.qty && l.price)
        .map(l => ({ materialId: l.materialId, qty: l.qty, price: l.price, remark: l.remark }))
      if (lines.length === 0) {
        this.$message.warning('至少一行有效明细（物料/数量/单价）')
        return
      }
      http.post('/crm/quotations', { ...this.form, lines }).then(body => {
        this.$message.success('已创建报价单 id=' + body.data)
        this.dialog = false
        this.load()
      }).catch(showError)
    },
    submit() {
      http.post(`/crm/quotations/${this.detail.id}/submit`).then(() => {
        this.$message.success('已提交评审')
        this.load()
      }).catch(showError)
    },
    decide(result) {
      http.post(`/crm/quotations/${this.detail.id}/decide`, { result }).then(body => {
        this.$message.success(result === 'WON' ? '已定标' : '已标记未中标')
        const data = body.data || {}
        if (data.contractId) {
          this.$notify.success({ title: '合同桥接（FR-CRM-19）', message: '已生成合同草稿 id=' + data.contractId })
        }
        this.load()
      }).catch(showError)
    },
    showPriceBasis() {
      http.get(`/crm/quotations/${this.detail.id}/price-basis`).then(body => {
        this.convText = JSON.stringify(body.data, null, 2)
        this.convDialog = true
      }).catch(showError)
    },
    loadConversion() {
      http.get('/crm/quotations/conversion-by-source').then(body => {
        this.convText = JSON.stringify(body.data, null, 2)
        this.convDialog = true
      }).catch(showError)
    }
  }
}
</script>
