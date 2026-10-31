<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-button v-permission="'scm:req:create'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">新建采购需求</el-button>
      </div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="0"
                aria-label="采购需求台账" empty-type="no-data" empty-label="采购需求"
                empty-action-text="新建采购需求" @empty-action="dialog = true">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status || 'PENDING'"
                         :dict="'PENDING:待处理,APPROVED:已审批,ORDERED:已下单,CANCELLED:已取消'" />
        </template>
      </pt-table>
    </el-card>
    <el-dialog title="新建采购需求（四来源：安全库存/MO 缺料/请购/手工）" :visible.sync="dialog" width="460px">
      <el-form label-width="100px" size="small">
        <el-form-item label="项目ID" required><el-input v-model.trim="form.projectId" /></el-form-item>
        <el-form-item label="物料ID" required><el-input v-model.trim="form.materialId" /></el-form-item>
        <el-form-item label="数量" required><el-input v-model.trim="form.qty" /></el-form-item>
        <el-form-item label="需求日期"><el-date-picker v-model="form.needBy" type="date" value-format="yyyy-MM-dd" style="width:100%" /></el-form-item>
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
  name: 'Requirement',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      columns: [
        { prop: 'requirementNo', label: '需求编号', width: 170 },
        { prop: 'projectId', label: '项目', width: 80 },
        { prop: 'materialId', label: '物料', width: 80 },
        { prop: 'qty', label: '数量', width: 90, align: 'right' },
        { prop: 'needBy', label: '需求日期', width: 112 },
        { prop: 'status', label: '状态', width: 100 }
      ],
      records: [], loading: false, dialog: false, form: {}
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/scm/requirements', { params: { pageNum: 1, pageSize: 50 } }).then(body => {
        this.records = body.data.records || body.data || []
      }).catch(showError).finally(() => { this.loading = false })
    },
    create() {
      this.$http.post('/scm/requirements', this.form).then(() => {
        this.$message.success('采购需求已创建')
        this.dialog = false
        this.load()
      }).catch(showError)
    }
  }
}
</script>
