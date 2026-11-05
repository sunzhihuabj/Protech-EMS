<template>
  <div class="pl-page">
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <div slot="header" class="pl-toolbar">
            <b>字典类型（E-01~E-42 为系统内置）</b>
            <div class="spacer"></div>
            <el-button v-permission="'org:dict:manage'" size="mini" type="primary" @click="openCreateType">新增</el-button>
          </div>
          <el-table :data="types" v-loading="loading" size="mini" highlight-current-row @current-change="selectType">
            <el-table-column prop="typeCode" label="编码" min-width="140" />
            <el-table-column prop="typeName" label="名称" min-width="130" />
            <el-table-column label="内置" width="60">
              <template slot-scope="{ row }">
                <el-tag v-if="row.isSystem" size="mini" type="warning">系统</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template slot-scope="{ row }">
                <el-button size="mini" type="text" @click.stop="openEditType(row)">编辑</el-button>
                <el-button size="mini" type="text" class="danger" @click.stop="removeType(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never">
          <div slot="header" class="pl-toolbar">
            <b>字典项{{ currentType ? `：${currentType.typeName}` : '' }}</b>
            <div class="spacer"></div>
            <el-button v-permission="'org:dict:manage'" size="mini" type="primary" :disabled="!currentType" @click="openCreateItem">新增项</el-button>
          </div>
          <div v-if="!currentType" class="empty">左侧选择一个字典类型</div>
          <el-table v-else :data="items" size="mini" v-loading="itemsLoading">
            <el-table-column prop="itemCode" label="code" min-width="140" />
            <el-table-column prop="itemLabel" label="名称" min-width="140" />
            <el-table-column prop="sortNo" label="排序" width="70" />
            <el-table-column prop="status" label="状态" width="80" />
            <el-table-column label="操作" width="80">
              <template slot-scope="{ row }">
                <el-button size="mini" type="text" @click="openEditItem(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog :title="typeDialogIsEdit ? '编辑字典类型' : '新增字典类型'" :visible.sync="typeDialog" width="420px">
      <el-form label-width="80px" size="small">
        <el-form-item label="编码" required>
          <el-input v-model.trim="typeForm.typeCode" :disabled="typeDialogIsEdit" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model.trim="typeForm.typeName" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="typeDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveType">保存</el-button>
      </span>
    </el-dialog>

    <el-dialog :title="itemForm.id ? '编辑字典项' : '新增字典项'" :visible.sync="itemDialog" width="420px">
      <el-form label-width="80px" size="small">
        <el-form-item label="code" required>
          <el-input v-model.trim="itemForm.itemCode" placeholder="英文大写（系统内置项禁改）" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model.trim="itemForm.itemLabel" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sortNo" :min="0" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="itemDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveItem">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'DictsPage',
  data() {
    return {
      types: [],
      items: [],
      currentType: null,
      loading: false,
      itemsLoading: false,
      typeDialog: false,
      typeDialogIsEdit: false,
      typeForm: {},
      itemDialog: false,
      itemForm: {}
    }
  },
  mounted() {
    this.loadTypes()
  },
  methods: {
    loadTypes() {
      this.loading = true
      this.$http.get('/system/dicts').then(body => { this.types = body.data || [] })
        .catch(e => this.$showError(e)).finally(() => { this.loading = false })
    },
    selectType(row) {
      this.currentType = row
      if (row) {
        this.itemsLoading = true
        this.$http.get('/system/dicts/items', { params: { typeId: row.id } }).then(body => {
          this.items = body.data || []
        }).catch(e => this.$showError(e)).finally(() => { this.itemsLoading = false })
      } else {
        this.items = []
      }
    },
    openCreateType() {
      this.typeForm = {}
      this.typeDialogIsEdit = false
      this.typeDialog = true
    },
    openEditType(row) {
      this.typeForm = { ...row }
      this.typeDialogIsEdit = true
      this.typeDialog = true
    },
    saveType() {
      const req = this.typeDialogIsEdit
        ? this.$http.put(`/system/dicts/types/${this.typeForm.id}`, this.typeForm)
        : this.$http.post('/system/dicts/types', this.typeForm)
      req.then(() => {
        this.$message.success('已保存')
        this.typeDialog = false
        this.loadTypes()
      }).catch(e => this.$showError(e))
    },
    removeType(row) {
      this.$confirm(`确认删除字典「${row.typeName}」？系统内置枚举将被拒绝（422）`, '删除确认', { type: 'warning' })
        .then(() => this.$http.delete(`/system/dicts/types/${row.id}`))
        .then(() => {
          this.$message.success('已删除')
          this.loadTypes()
        }).catch(e => { if (e !== 'cancel') this.$showError(e) })
    },
    openCreateItem() {
      this.itemForm = { typeId: this.currentType.id, sortNo: 0 }
      this.itemDialog = true
    },
    openEditItem(row) {
      this.itemForm = { ...row }
      this.itemDialog = true
    },
    saveItem() {
      const req = this.itemForm.id
        ? this.$http.put(`/system/dicts/items/${this.itemForm.id}`, this.itemForm)
        : this.$http.post('/system/dicts/items', this.itemForm)
      req.then(() => {
        this.$message.success('已保存')
        this.itemDialog = false
        this.selectType(this.currentType)
      }).catch(e => this.$showError(e))
    }
  }
}
</script>

<style scoped>
.empty { color: var(--ink-4); text-align: center; padding: 32px 0; }
.danger { color: var(--danger); }
</style>
