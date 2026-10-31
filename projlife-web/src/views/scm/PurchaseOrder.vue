<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-button v-permission="'scm:order:create'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">新建 PO</el-button>
      </div>
      <el-table :data="orders" v-loading="loading" size="small" @row-click="showDetail">
        <el-table-column prop="poNo" label="PO 编号" width="170" />
        <el-table-column prop="supplierId" label="供应商" width="90" />
        <el-table-column prop="amount" label="金额(元)" width="130" align="right" />
        <el-table-column prop="status" label="状态" width="120">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="row.status === 'COMPLETED' ? 'success' : row.status === 'DISPATCHED' ? '' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="orders.length === 0 && !loading" description="暂无采购订单" :image-size="50" />
    </el-card>
    <el-drawer :visible.sync="detailDrawer" size="45%" :title="detail ? detail.poNo : 'PO 详情'">
      <div style="padding: 0 16px" v-if="detail">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="PO 编号">{{ detail.poNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ detail.amount }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ detail.supplierId }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 12px">
          <el-button v-if="detail.status === 'CREATED'" v-permission="'scm:order:dispatch'" size="small" type="primary" @click="dispatch">下发订单</el-button>
        </div>
      </div>
    </el-drawer>
    <el-dialog title="新建采购订单" :visible.sync="dialog" width="440px">
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
import { showError } from '@/api/request'
export default {
  name: 'PurchaseOrder',
  data() { return { orders: [], loading: false, dialog: false, form: {}, detail: null, detailDrawer: false } },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/scm/orders', { params: { pageNum: 1, pageSize: 50 } }).then(body => { this.orders = body.data.records || body.data || [] }).catch(showError).finally(() => { this.loading = false })
    },
    showDetail(row) {
      this.$http.get(`/scm/orders/${row.id}`).then(body => { this.detail = body.data; this.detailDrawer = true }).catch(showError)
    },
    create() {
      this.$http.post('/scm/orders', this.form).then(() => { this.$message.success('PO 已创建'); this.dialog = false; this.load() }).catch(showError)
    },
    dispatch() {
      this.$http.post(`/scm/orders/${this.detail.id}/dispatch`).then(() => { this.$message.success('已下发'); this.load() }).catch(showError)
    }
  }
}
</script>
