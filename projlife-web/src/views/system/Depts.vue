<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div class="pl-toolbar">
        <b>组织部门（14 部门树）</b>
        <div class="spacer"></div>
        <el-button v-permission="'org:dept:manage'" size="small" type="primary" icon="el-icon-plus" @click="openCreate()">新增部门</el-button>
      </div>
      <el-table :data="tree" v-loading="loading" size="small" row-key="id"
                :tree-props="{ children: 'children' }" default-expand-all>
        <el-table-column prop="deptName" label="部门" min-width="220" />
        <el-table-column prop="deptCode" label="编码" width="110" />
        <el-table-column prop="deptType" label="类型" width="100" />
        <el-table-column label="链主" width="80">
          <template slot-scope="{ row }">
            <el-tag v-if="row.isChainMaster" size="mini" type="warning">链主</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="操作" width="160">
          <template slot-scope="{ row }">
            <el-button size="mini" type="text" @click="openCreate(row)">添加子级</el-button>
            <el-button size="mini" type="text" @click="openEdit(row)">编辑</el-button>
            <el-button size="mini" type="text" class="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="form.id ? '编辑部门' : '新增部门'" :visible.sync="dialog" width="480px">
      <el-form label-width="90px" size="small">
        <el-form-item label="部门编码" required>
          <el-input v-model.trim="form.deptCode" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="部门名称" required>
          <el-input v-model.trim="form.deptName" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-select v-model="form.parentId" clearable style="width: 100%">
            <el-option v-for="d in flatDepts" :key="d.id" :value="d.id" :label="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.deptType" style="width: 100%">
            <el-option value="COMPANY" label="公司" />
            <el-option value="DEPT" label="部门" />
            <el-option value="GROUP" label="组" />
          </el-select>
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
  name: 'DeptsPage',
  data() {
    return {
      tree: [],
      flatDepts: [],
      loading: false,
      dialog: false,
      form: {}
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/org/depts/tree').then(body => {
        this.tree = body.data || []
        this.flatDepts = []
        this.flatten(this.tree, '')
      }).catch(e => this.$showError(e)).finally(() => { this.loading = false })
    },
    flatten(nodes, prefix) {
      nodes.forEach(n => {
        const label = prefix + n.deptName
        this.flatDepts.push({ id: n.id, label })
        if (n.children && n.children.length) {
          this.flatten(n.children, label + ' / ')
        }
      })
    },
    openCreate(parent) {
      this.form = { deptType: 'DEPT', parentId: parent ? parent.id : null }
      this.dialog = true
    },
    openEdit(row) {
      this.form = { ...row }
      this.dialog = true
    },
    save() {
      if (!this.form.deptCode || !this.form.deptName) {
        this.$message.warning('编码与名称必填')
        return
      }
      const req = this.form.id
        ? this.$http.put(`/org/depts/${this.form.id}`, this.form)
        : this.$http.post('/org/depts', this.form)
      req.then(() => {
        this.$message.success('已保存')
        this.dialog = false
        this.load()
      }).catch(e => this.$showError(e))
    },
    remove(row) {
      this.$confirm(`确认删除部门「${row.deptName}」？有下级或有在职用户将被拒绝`, '删除确认', { type: 'warning' })
        .then(() => this.$http.delete(`/org/depts/${row.id}`))
        .then(() => {
          this.$message.success('已删除')
          this.load()
        }).catch(e => { if (e !== 'cancel') this.$showError(e) })
    }
  }
}
</script>

<style scoped>
.danger { color: var(--danger); }
</style>
