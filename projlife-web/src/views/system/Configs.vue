<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div slot="header"><b>系统参数（app.theme.default 由 R03 配置，03 册 §6A.1）</b></div>
      <el-table :data="configs" v-loading="loading" size="small">
        <el-table-column prop="configGroup" label="分组" width="110" />
        <el-table-column prop="configKey" label="键" min-width="220" />
        <el-table-column prop="configValue" label="值" min-width="220" show-overflow-tooltip />
        <el-table-column prop="remark" label="说明" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="90">
          <template slot-scope="{ row }">
            <el-button v-permission="'system:config:manage'" size="mini" type="text" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="编辑参数" :visible.sync="dialog" width="480px">
      <el-form label-width="80px" size="small">
        <el-form-item label="键">
          <el-input :value="form.configKey" disabled />
        </el-form-item>
        <el-form-item label="值" required>
          <el-input v-model.trim="form.configValue" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="save">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'ConfigsPage',
  data() {
    return { configs: [], loading: false, dialog: false, form: {} }
  },
  mounted() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/system/configs').then(body => { this.configs = body.data || [] })
        .catch(e => this.$showError(e)).finally(() => { this.loading = false })
    },
    openEdit(row) {
      this.form = { ...row }
      this.dialog = true
    },
    save() {
      this.$http.put('/system/configs', this.form).then(() => {
        this.$message.success('已保存（配置变更已留审计）')
        this.dialog = false
        this.load()
      }).catch(e => this.$showError(e))
    }
  }
}
</script>
