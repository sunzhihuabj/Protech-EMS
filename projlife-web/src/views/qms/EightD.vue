<template>
  <!-- 8D 报告（FR-QMS-08~13）：列表 + 新建 + 关闭（D6 校验，R18 补列表端点与关闭入口） -->
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-select v-model="query.status" size="small" style="width: 140px" clearable placeholder="状态" @change="onFilter">
          <el-option value="OPEN" label="处理中" />
          <el-option value="CLOSED" label="已关闭" />
        </el-select>
        <el-button size="small" type="primary" icon="el-icon-search" @click="onFilter">查询</el-button>
        <div class="spacer"></div>
        <el-button v-permission="'qms:8d:manage'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">新建 8D</el-button>
      </div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="total"
                :page.sync="page" @page-change="onPage"
                aria-label="8D 报告台账" empty-type="no-data" empty-label="8D 报告"
                empty-action-text="新建 8D" @empty-action="dialog = true">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status || 'OPEN'" :dict="'OPEN:处理中,CLOSED:已关闭'" />
        </template>
        <template slot="cell-d6Verify" slot-scope="{ row }">
          <span :class="{ 'muted': !row.d6Verify }">{{ row.d6Verify || '未填写（D6 校验：关闭前必填）' }}</span>
        </template>
        <template slot="actions" slot-scope="{ row }">
          <el-button v-if="row.status !== 'CLOSED'" v-permission="'qms:8d:manage'" size="mini" type="text" @click="openClose(row)">关闭</el-button>
        </template>
      </pt-table>
    </el-card>

    <!-- 新建 8D 弹窗 -->
    <el-dialog title="新建 8D 报告（D1-D3 填写，D6 关闭校验）" :visible.sync="dialog" width="480px">
      <el-form label-width="110px" size="small">
        <el-form-item label="问题描述" required>
          <el-input v-model="form.problemDesc" type="textarea" :rows="3" placeholder="5W2H 描述问题" />
        </el-form-item>
        <el-form-item label="来源检验ID"><el-input v-model.trim="form.sourceInspectionId" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">发起 8D</el-button>
      </div>
    </el-dialog>

    <!-- 关闭 8D 弹窗（后端校验 D1/D4/D6 必填，R18 起关闭请求可回填步骤内容） -->
    <el-dialog title="关闭 8D 报告（D1/D4/D6 关闭校验）" :visible.sync="closeDialog" width="520px">
      <div v-if="closeTarget" class="muted" style="margin-bottom: 10px">
        {{ closeTarget.reportNo }} · {{ (closeTarget.problemDesc || '').slice(0, 40) }}
      </div>
      <el-form label-width="110px" size="small">
        <el-form-item label="D1 团队" required>
          <el-input v-model="closeForm.d1Team" placeholder="小组成员及角色（组长/质量/工艺/生产）" />
        </el-form-item>
        <el-form-item label="D4 根因" required>
          <el-input v-model="closeForm.d4RootCause" type="textarea" :rows="2" placeholder="根本原因分析（5Why 等）" />
        </el-form-item>
        <el-form-item label="D5 纠正措施">
          <el-input v-model="closeForm.d5Corrective" type="textarea" :rows="2" placeholder="永久对策（选填）" />
        </el-form-item>
        <el-form-item label="D6 效果验证" required>
          <el-input v-model="closeForm.d6Verify" type="textarea" :rows="2" placeholder="效果验证结论（对策后连续 N 批无复发等）" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="closeDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doClose">确认关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { PtTable, PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'
export default {
  name: 'EightD',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      columns: [
        { prop: 'reportNo', label: '报告编号', width: 150 },
        { prop: 'problemDesc', label: '问题描述', minWidth: 200 },
        { prop: 'ownerId', label: '负责人', width: 90 },
        { prop: 'd6Verify', label: 'D6 效果验证', minWidth: 160 },
        { prop: 'status', label: '状态', width: 100 },
        { prop: 'closedAt', label: '关闭时间', width: 160 }
      ],
      query: { pageNum: 1, pageSize: 20, status: '' },
      page: { pageNum: 1, pageSize: 20 },
      records: [], total: 0, loading: false,
      dialog: false, form: {},
      closeDialog: false, closeTarget: null,
      closeForm: { d1Team: '', d4RootCause: '', d5Corrective: '', d6Verify: '' }
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
      this.$http.get('/qms/8ds', { params }).then(body => {
        const pageData = body.data || {}
        this.records = pageData.records || []
        this.total = pageData.total || 0
      }).catch(showError).finally(() => { this.loading = false })
    },
    create() {
      if (!this.form.problemDesc) { this.$message.warning('问题描述必填'); return }
      this.$http.post('/qms/8ds', this.form).then(body => {
        this.$message.success('8D 报告已发起 ' + body.data)
        this.dialog = false
        this.form = {}
        this.load()
      }).catch(showError)
    },
    openClose(row) {
      this.closeTarget = row
      this.closeForm = {
        d1Team: row.d1Team || '',
        d4RootCause: row.d4RootCause || '',
        d5Corrective: row.d5Corrective || '',
        d6Verify: row.d6Verify || ''
      }
      this.closeDialog = true
    },
    doClose() {
      if (!this.closeForm.d1Team || !this.closeForm.d4RootCause || !this.closeForm.d6Verify) {
        this.$message.warning('D1 团队 / D4 根因 / D6 效果验证均必填（FR-QMS-13 关闭校验）')
        return
      }
      this.$http.post(`/qms/8ds/${this.closeTarget.id}/close`, this.closeForm)
        .then(() => {
          this.$message.success('8D 报告已关闭')
          this.closeDialog = false
          this.load()
        }).catch(showError)
    }
  }
}
</script>

<style scoped>
.muted { color: var(--ink-3); font-size: 12px; }
.spacer { flex: 1; }
</style>
