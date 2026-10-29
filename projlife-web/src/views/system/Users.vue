<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div class="pl-toolbar">
        <el-input v-model="query.keyword" placeholder="姓名/登录名/工号" size="small" style="width: 220px" clearable
                  @keyup.enter.native="load" @clear="load" />
        <el-button size="small" type="primary" icon="el-icon-search" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button v-permission="'org:user:manage'" size="small" @click="openImport">批量导入</el-button>
        <el-button v-permission="'org:user:manage'" size="small" @click="exportUsers">导出</el-button>
        <el-button v-permission="'org:user:manage'" size="small" type="primary" icon="el-icon-plus" @click="openCreate">新增用户</el-button>
      </div>

      <el-table :data="records" v-loading="loading" size="small" row-key="id">
        <el-table-column prop="userCode" label="工号" width="90" />
        <el-table-column prop="username" label="登录名" min-width="140" />
        <el-table-column prop="realName" label="姓名" width="110" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="deptName" label="部门" width="110">
          <template slot-scope="{ row }">{{ deptName(row.deptId) }}</template>
        </el-table-column>
        <el-table-column prop="roleNames" label="角色" min-width="140">
          <template slot-scope="{ row }">{{ (row.roleNames || []).join('、') }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" width="160" />
        <el-table-column label="操作" width="220">
          <template slot-scope="{ row }">
            <el-button size="mini" type="text" @click="openEdit(row)">编辑</el-button>
            <el-button size="mini" type="text" @click="toggleFreeze(row)">{{ row.status === 'ACTIVE' ? '冻结' : '解冻' }}</el-button>
            <el-button size="mini" type="text" @click="resetPwd(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination background layout="total, prev, pager, next" :total="total"
                     :page-size="query.pageSize" :current-page.sync="query.pageNum" @current-change="load" />
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog :title="form.id ? '编辑用户' : '新增用户'" :visible.sync="dialog" width="560px">
      <el-form label-width="90px" size="small">
        <el-form-item label="工号" required>
          <el-input v-model.trim="form.userCode" :disabled="!!form.id" placeholder="如 E0101" />
        </el-form-item>
        <el-form-item label="登录名" required>
          <el-input v-model.trim="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model.trim="form.realName" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" required>
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位，首登强制修改" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model.trim="form.phone" />
        </el-form-item>
        <el-form-item label="部门" required>
          <el-select v-model="form.deptId" filterable style="width: 100%">
            <el-option v-for="d in flatDepts" :key="d.id" :value="d.id" :label="d.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位">
          <el-input v-model.trim="form.position" />
        </el-form-item>
        <el-form-item label="用户类型">
          <el-select v-model="form.userType" style="width: 100%">
            <el-option v-for="(label, code) in userTypes" :key="code" :value="code" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :value="r.id" :label="`${r.roleCode} ${r.roleName}`" />
          </el-select>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="save">保存</el-button>
      </span>
    </el-dialog>

    <!-- 批量导入（S0-10：干跑 → 报告 → 确认提交） -->
    <el-dialog title="用户批量导入" :visible.sync="importDialog" width="640px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px"
                title="模板列：工号/登录名/姓名/手机号/部门编码/岗位/用户类型；先干跑校验，全部通过后正式提交" />
      <el-form size="small" inline>
        <el-form-item>
          <el-switch v-model="importDryRun" active-text="干跑校验" inactive-text="正式提交" />
        </el-form-item>
        <el-form-item>
          <input ref="fileInput" type="file" accept=".xlsx" />
        </el-form-item>
        <el-form-item>
          <el-button size="small" type="primary" @click="doImport">执行</el-button>
        </el-form-item>
      </el-form>
      <div v-if="importResult">
        <p>批次 {{ importResult.batchNo }} · 总 {{ importResult.totalRows }} · 成功 {{ importResult.successRows }} · 失败 {{ importResult.failRows }}</p>
        <el-table v-if="importResult.errors && importResult.errors.length" :data="importResult.errors" size="mini" max-height="240">
          <el-table-column prop="0" label="行号" width="70" />
          <el-table-column prop="1" label="错误明细" />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'UsersPage',
  data() {
    return {
      records: [],
      total: 0,
      loading: false,
      query: { pageNum: 1, pageSize: 20, keyword: '' },
      dialog: false,
      form: {},
      roles: [],
      deptTree: [],
      flatDepts: [],
      userTypes: { INTERNAL: '内部员工', SUPPLIER: '供应商人员', WORKER: '施工人员' },
      importDialog: false,
      importDryRun: true,
      importResult: null
    }
  },
  mounted() {
    this.load()
    this.loadRoles()
    this.loadDepts()
  },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/org/users', { params: this.query }).then(body => {
        this.records = (body.data && body.data.records) || []
        this.total = (body.data && body.data.total) || 0
      }).catch(e => this.$showError(e)).finally(() => { this.loading = false })
    },
    loadRoles() {
      this.$http.get('/org/roles').then(body => { this.roles = body.data || [] }).catch(() => {})
    },
    loadDepts() {
      this.$http.get('/org/depts/tree').then(body => {
        this.deptTree = body.data || []
        this.flatDepts = []
        this.flatten(this.deptTree, '')
      }).catch(() => {})
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
    deptName(deptId) {
      const dept = this.flatDepts.find(d => d.id === deptId)
      return dept ? dept.label.replace(/.*\/\s*/, '') : deptId
    },
    openCreate() {
      this.form = { userType: 'INTERNAL', roleIds: [] }
      this.dialog = true
    },
    openEdit(row) {
      this.form = { ...row, roleIds: row.roleIds || [] }
      this.dialog = true
    },
    save() {
      if (!this.form.userCode || !this.form.username || !this.form.realName || !this.form.deptId) {
        this.$message.warning('工号/登录名/姓名/部门为必填')
        return
      }
      const req = this.form.id
        ? this.$http.put(`/org/users/${this.form.id}`, this.form)
        : this.$http.post('/org/users', this.form)
      req.then(() => {
        this.$message.success('已保存')
        this.dialog = false
        this.load()
      }).catch(e => this.$showError(e))
    },
    toggleFreeze(row) {
      const status = row.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE'
      this.$http.put(`/org/users/${row.id}/status`, { status }).then(() => {
        this.$message.success(status === 'ACTIVE' ? '已解冻' : '已冻结')
        this.load()
      }).catch(e => this.$showError(e))
    },
    resetPwd(row) {
      this.$prompt('输入新密码（至少 8 位，用户首登强制修改）', `重置密码：${row.realName}`, {
        inputType: 'password',
        inputPattern: /^.{8,64}$/,
        inputErrorMessage: '密码长度需在 8-64 位之间'
      }).then(({ value }) => {
        return this.$http.put(`/org/users/${row.id}/password/reset`, { newPassword: value })
      }).then(() => {
        this.$message.success('已重置')
      }).catch(e => { if (e !== 'cancel') this.$showError(e) })
    },
    openImport() {
      this.importResult = null
      this.importDryRun = true
      this.importDialog = true
    },
    doImport() {
      const fileEl = this.$refs.fileInput
      if (!fileEl.files || !fileEl.files[0]) {
        this.$message.warning('请先选择 xlsx 文件')
        return
      }
      const formData = new FormData()
      formData.append('file', fileEl.files[0])
      formData.append('dryRun', this.importDryRun)
      this.$http.post('/org/users/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
        .then(body => {
          this.importResult = body.data
          if (!this.importDryRun) {
            this.$message.success('导入完成')
            this.load()
          }
        }).catch(e => this.$showError(e))
    },
    exportUsers() {
      this.$http.post('/org/users/export', null, { responseType: 'blob' }).then(blob => {
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '用户清单.xlsx'
        a.click()
        URL.revokeObjectURL(url)
      }).catch(e => this.$showError(e))
    }
  }
}
</script>
