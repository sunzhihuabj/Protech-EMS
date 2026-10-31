<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <el-button v-permission="'qms:inspect'" size="small" type="primary" icon="el-icon-plus" @click="dialog = true">登记设备</el-button>
      </div>
      <el-table :data="assets" v-loading="loading" size="small">
        <el-table-column prop="assetCode" label="设备编码" width="140" />
        <el-table-column prop="assetName" label="设备名称" min-width="180" />
        <el-table-column prop="assetType" label="类型" width="100" />
        <el-table-column prop="manufacturer" label="制造商" width="140" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="110">
          <template slot-scope="{ row }">
            <el-button size="mini" type="text" @click="viewMtbf(row)">MTBF</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="assets.length === 0 && !loading" description="暂无设备" :image-size="50" />
    </el-card>
    <el-dialog title="登记设备" :visible.sync="dialog" width="440px">
      <el-form label-width="100px" size="small">
        <el-form-item label="设备名称" required><el-input v-model.trim="form.assetName" /></el-form-item>
        <el-form-item label="设备类型"><el-input v-model.trim="form.assetType" /></el-form-item>
        <el-form-item label="制造商"><el-input v-model.trim="form.manufacturer" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">登记</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { showError } from '@/api/request'
export default {
  name: 'EamAsset',
  data() { return { assets: [], loading: false, dialog: false, form: {} } },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/eam/assets').then(body => { this.assets = (body.data && body.data.records) || body.data || [] }).catch(showError).finally(() => { this.loading = false })
    },
    create() {
      this.$http.post('/eam/assets', this.form).then(() => { this.$message.success('设备已登记'); this.dialog = false; this.load() }).catch(showError)
    },
    viewMtbf(row) {
      this.$http.get(`/eam/assets/${row.id}/mtbf`).then(body => {
        this.$notify.info({ title: 'MTBF', message: JSON.stringify(body.data) })
      }).catch(showError)
    }
  }
}
</script>
