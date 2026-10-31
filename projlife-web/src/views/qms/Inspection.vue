<template>
  <!-- 检验台账（FR-QMS-02~07）：列表 + 状态 Tag + 操作 -->
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-select v-model="query.inspectionType" size="small" style="width: 130px" clearable placeholder="类型" @change="load">
          <el-option value="IQC" label="IQC 来料" /><el-option value="IPQC" label="IPQC 制程" />
          <el-option value="OQC" label="OQC 出货" /><el-option value="FQC" label="FQC 成品" />
        </el-select>
        <el-button size="small" type="primary" icon="el-icon-search" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button v-permission="'qms:inspect'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">新建检验</el-button>
      </div>

      <pt-table :columns="columns" :data="records" :loading="loading" :total="total"
                :page.sync="page" aria-label="检验台账" empty-type="no-data"
                empty-label="检验单" empty-action-text="新建检验" @empty-action="dialog = true">
        <template slot="cell-inspectionType" slot-scope="{ row }">
          <pt-status-tag :value="row.inspectionType" :dict="'IQC:IQC 来料,IPQC:IPQC 制程,OQC:OQC 出货,FQC:FQC 成品'" />
        </template>
        <template slot="cell-result" slot-scope="{ row }">
          <pt-status-tag :value="row.result || 'PENDING'"
                         :dict="'PASS:合格,FAIL:不合格,PENDING:待检,CONCESSION:让步'" />
        </template>
      </pt-table>
    </el-card>

    <el-dialog title="新建检验单" :visible.sync="dialog" width="480px">
      <el-form label-width="90px" size="small">
        <el-form-item label="类型" required>
          <el-select v-model="form.inspectionType" style="width: 100%">
            <el-option value="IQC" label="IQC 来料" /><el-option value="IPQC" label="IPQC 制程" />
            <el-option value="OQC" label="OQC 出货" /><el-option value="FQC" label="FQC 成品" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源类型"><el-input v-model.trim="form.sourceType" placeholder="MO/PO/..." /></el-form-item>
        <el-form-item label="来源ID"><el-input v-model.trim="form.sourceId" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">创建</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { PtTable, PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'

export default {
  name: 'QmsInspection',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      columns: [
        { prop: 'inspectionNo', label: '检验单号', width: 170 },
        { prop: 'inspectionType', label: '类型', width: 120 },
        { prop: 'sourceType', label: '来源', width: 90 },
        { prop: 'sourceId', label: '来源ID', width: 90 },
        { prop: 'result', label: '结果', width: 110 },
        { prop: 'inspectorId', label: '检验员', width: 90 },
        { prop: 'inspectionTime', label: '检验时间', width: 160 }
      ],
      query: { pageNum: 1, pageSize: 20, inspectionType: '' },
      page: { pageNum: 1, pageSize: 20 },
      records: [], total: 0, loading: false,
      dialog: false, form: {}
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.query.pageNum = this.page.pageNum
      this.query.pageSize = this.page.pageSize
      this.$http.get('/qms/inspections', { params: this.query }).then(body => {
        this.records = body.data.records || []
        this.total = body.data.total || 0
      }).catch(showError).finally(() => { this.loading = false })
    },
    create() {
      this.$http.post('/qms/inspections', this.form).then(body => {
        this.$message.success('检验单已创建 ' + body.data)
        this.dialog = false
        this.load()
      }).catch(showError)
    }
  }
}
</script>
