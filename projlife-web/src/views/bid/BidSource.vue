<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <span style="font-weight:600">数据源管理</span>
        <div class="spacer"></div>
        <el-button v-permission="'bid:source:manage'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">登记数据源</el-button>
      </div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="0"
                aria-label="数据源" empty-type="no-data" empty-label="数据源" empty-action-text="登记数据源" @empty-action="dialog = true">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status || 'PENDING_REVIEW'" :dict="'PENDING_REVIEW:待审核,ACTIVE:抓取中,DISABLED:已停用'" />
        </template>
        <template slot="actions" slot-scope="{ row }">
          <el-button v-if="row.status !== 'ACTIVE'" v-permission="'bid:source:manage'" size="mini" type="text" @click.stop="review(row)">启用</el-button>
          <el-button v-else v-permission="'bid:source:manage'" size="mini" type="text" @click.stop="review(row)">停用</el-button>
        </template>
      </pt-table>
    </el-card>
    <el-dialog title="登记数据源" :visible.sync="dialog" width="440px">
      <el-form label-width="90px" size="small">
        <el-form-item label="类别" required>
          <el-select v-model="form.category" style="width:100%">
            <el-option value="A_OWNER" label="A 业主官网" /><el-option value="B_OEM" label="B 主机厂" />
            <el-option value="C_PORTAL" label="C 公共平台" /><el-option value="D_MANUAL" label="D 人工录入" />
          </el-select>
        </el-form-item>
        <el-form-item label="入口 URL" required><el-input v-model.trim="form.entryUrl" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">登记</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { PtTable, PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'
export default {
  name: 'BidSource',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      columns: [
        { prop: 'sourceCode', label: '源编号', width: 130 },
        { prop: 'category', label: '类别', width: 110 },
        { prop: 'entryUrl', label: '入口', minWidth: 220 },
        { prop: 'status', label: '状态', width: 120 },
        { prop: 'robotsCheckedAt', label: 'robots 检查', width: 160 }
      ],
      records: [], loading: false, dialog: false, form: {}
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/bid/sources', { params: { pageNum: 1, pageSize: 50 } }).then(body => {
        this.records = body.data.records || []
      }).catch(showError).finally(() => { this.loading = false })
    },
    create() {
      this.$http.post('/bid/sources', this.form).then(() => { this.$message.success('已登记'); this.dialog = false; this.load() }).catch(showError)
    },
    review(row) {
      const active = row.status !== 'ACTIVE'
      this.$http.post(`/bid/sources/${row.id}/review`, { status: active ? 'ACTIVE' : 'DISABLED', robotsStatusCode: active ? 200 : null })
        .then(() => { this.$message.success(active ? '已启用' : '已停用'); this.load() }).catch(showError)
    }
  }
}
</script>
