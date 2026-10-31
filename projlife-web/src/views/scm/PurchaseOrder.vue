<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-button v-permission="'scm:order:create'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">新建 PO</el-button>
      </div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="0"
                aria-label="PO 台账" empty-type="no-data" empty-label="采购订单"
                empty-action-text="新建 PO" @empty-action="dialog = true">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status || 'CREATED'"
                         :dict="'CREATED:已创建,DISPATCHED:已下发,RECEIVING:到货中,COMPLETED:已完成,CLOSED:已关闭'" />
        </template>
      </pt-table>
    </el-card>
    <el-dialog title="新建采购订单" :visible.sync="dialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="项目ID" required><el-input v-model.trim="form.projectId" /></el-form-item>
        <el-form-item label="供应商ID" required><el-input v-model.trim="form.supplierId" /></el-form-item>
        <el-form-item label="金额"><el-input v-model.trim="form.amount" /></el-form-item>
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
  name: 'PurchaseOrder',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      columns: [
        { prop: 'poNo', label: 'PO 编号', width: 170 },
        { prop: 'supplierId', label: '供应商', width: 90 },
        { prop: 'amount', label: '金额(元)', type: 'money', align: 'right' },
        { prop: 'status', label: '状态', width: 120 }
      ],
      records: [], loading: false, dialog: false, form: {}
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.$http.get('/scm/orders', { params: { pageNum: 1, pageSize: 20 } }).then(body => {
        this.records = body.data.records || []
      }).catch(showError).finally(() => { this.loading = false })
    },
    create() {
      this.$http.post('/scm/orders', this.form).then(body => {
        this.$message.success('PO 已创建')
        this.dialog = false
        this.load()
      }).catch(showError)
    }
  }
}
</script>
