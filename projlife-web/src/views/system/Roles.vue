<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div class="pl-toolbar">
        <b>角色与权限（18 角色体系）</b>
        <div class="spacer"></div>
        <el-button v-permission="'org:role:manage'" size="small" type="primary" icon="el-icon-plus" @click="openCreate">新增角色</el-button>
      </div>
      <el-table :data="roles" v-loading="loading" size="small">
        <el-table-column prop="roleCode" label="编码" width="80" />
        <el-table-column prop="roleName" label="名称" width="150" />
        <el-table-column prop="dataScope" label="数据范围" width="110">
          <template slot-scope="{ row }">{{ scopeLabel(row.dataScope) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="260" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template slot-scope="{ row }">
            <el-button size="mini" type="text" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="form.id ? '编辑角色' : '新增角色'" :visible.sync="dialog" width="640px">
      <el-form label-width="90px" size="small">
        <el-form-item label="角色编码" required>
          <el-input v-model.trim="form.roleCode" :disabled="!!form.id" placeholder="R01-R18 或新增 Rxx" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model.trim="form.roleName" />
        </el-form-item>
        <el-form-item label="数据范围" required>
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="(label, code) in scopes" :key="code" :value="code" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="权限">
          <el-tree ref="permTree" :data="permTree" show-checkbox node-key="id" default-expand-all
                   :props="{ label: 'permName', children: 'children' }" style="max-height: 320px; overflow: auto" />
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
  name: 'RolesPage',
  data() {
    return {
      roles: [],
      loading: false,
      dialog: false,
      form: {},
      permTree: [],
      scopes: { SELF: '本人', DEPT: '本部门', DEPT_SUB: '本部门及下级', PROJECT: '项目成员', ALL: '全局' }
    }
  },
  mounted() {
    this.load()
    this.loadPermTree()
  },
  methods: {
    scopeLabel(code) {
      return this.scopes[code] || code
    },
    load() {
      this.loading = true
      this.$http.get('/org/roles').then(body => { this.roles = body.data || [] })
        .catch(e => this.$showError(e)).finally(() => { this.loading = false })
    },
    loadPermTree() {
      this.$http.get('/org/roles/permissions').then(body => { this.permTree = body.data || [] }).catch(() => {})
    },
    openCreate() {
      this.form = { dataScope: 'SELF' }
      this.dialog = true
      this.$nextTick(() => this.$refs.permTree && this.$refs.permTree.setCheckedKeys([]))
    },
    openEdit(row) {
      this.form = { ...row }
      this.dialog = true
      this.$nextTick(() => {
        if (this.$refs.permTree) {
          this.$refs.permTree.setCheckedKeys(row.permIds || [])
        }
      })
    },
    save() {
      const permIds = this.$refs.permTree.getCheckedKeys().concat(this.$refs.permTree.getHalfCheckedKeys())
      const payload = { ...this.form, permIds }
      const req = this.form.id
        ? this.$http.put(`/org/roles/${this.form.id}`, payload)
        : this.$http.post('/org/roles', payload)
      req.then(() => {
        this.$message.success('已保存，相关用户权限即时生效')
        this.dialog = false
        this.load()
      }).catch(e => this.$showError(e))
    }
  }
}
</script>
