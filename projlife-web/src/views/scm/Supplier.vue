<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-button v-permission="'scm:supplier:manage'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">新建供应商</el-button>
      </div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="0"
                aria-label="供应商台账" empty-type="no-data" empty-label="供应商"
                empty-action-text="新建供应商" @empty-action="dialog = true">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status || 'ACTIVE'" :dict="'ACTIVE:合格,FROZEN:冻结,PROBATION:试用'" />
        </template>
      </pt-table>
    </el-card>
    <el-dialog title="新建供应商" :visible.sync="dialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="供应商名称" required><el-input v-model.trim="form.supplierName" /></el-form-item>
        <el-form-item label="统一社会信用代码" required><el-input v-model.trim="form.creditCode" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model.trim="form.contact" /></el-form-item>
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
  name: 'Supplier',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      columns: [
        { prop: 'supplierName', label: '供应商名称', minWidth: 200 },
        { prop: 'creditCode', label: '统一社会信用代码', width: 180 },
        { prop: 'contact', label: '联系人', width: 110 },
        { prop: 'status', label: '状态', width: 90 }
      ],
      records: [], loading: false, dialog: false, form: {}
    }
  },
  methods: {
    create() {
      this.$http.post('/scm/suppliers', this.form).then(body => {
        this.$message.success('供应商已建档')
        this.dialog = false
      }).catch(showError)
    }
  }
}
</script>
