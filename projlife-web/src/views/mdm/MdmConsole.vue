<template>
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <el-tab-pane label="客户主体" name="customers">
        <div class="pl-toolbar">
          <el-button v-permission="'mdm:customer:create'" size="small" type="primary" icon="el-icon-plus" @click="custDialog = true">新建客户（C 编号）</el-button>
          <el-button size="small" icon="el-icon-refresh" @click="loadCustomers">刷新</el-button>
        </div>
        <pt-table :columns="custCols" :data="customers" :loading="custLoading"
                      :total="0" aria-label="客户主体台账" empty-type="no-data"
                      empty-label="客户" empty-action-text="新建客户" @empty-action="custDialog = true">
          <template slot="cell-status" slot-scope="{ row }">
            <pt-status-tag :value="row.status" :dict="'ACTIVE:启用,FROZEN:停用,PENDING:待激活'" />
          </template>
        </pt-table>
      </el-tab-pane>

      <el-tab-pane label="项目档案" name="projects">
        <el-table :data="mdProjects" v-loading="projLoading" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="projectCode" label="档案号" width="150" />
          <el-table-column prop="stdName" label="标准名" min-width="220" show-overflow-tooltip />
          <el-table-column prop="farmNameRaw" label="风场" width="150" show-overflow-tooltip />
          <el-table-column prop="phase" label="期次" width="70" />
          <el-table-column prop="setQty" label="台数" width="70" />
          <el-table-column prop="status" label="状态" width="130" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="物料主数据" name="materials">
        <div class="pl-toolbar">
          <el-button v-permission="'mdm:material:create'" size="small" type="primary" icon="el-icon-plus" @click="matDialog = true">新建物料</el-button>
        </div>
        <p class="muted">编码规则（C{4位} 客户 / 物料编码 / 单位 E-20 受控 8 种）由服务端强校验；同名同规格重复会命中三重查重。</p>
      </el-tab-pane>
    </el-tabs>

    <el-dialog title="新建客户（FR-MDM-01）" :visible.sync="custDialog" width="500px">
      <el-form label-width="110px" size="small">
        <el-form-item label="规范全称" required><el-input v-model="custForm.customerName" placeholder="与营业执照一致" /></el-form-item>
        <el-form-item label="简称"><el-input v-model.trim="custForm.shortName" /></el-form-item>
        <el-form-item label="层级" required>
          <el-select v-model="custForm.level" style="width: 100%">
            <el-option value="GROUP" label="集团 GROUP" /><el-option value="REGION" label="区域 REGION" /><el-option value="PROJECT" label="项目公司 PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="custForm.customerType" style="width: 100%">
            <el-option value="OWNER" label="业主 OWNER" /><el-option value="OEM" label="主机厂 OEM" />
            <el-option value="RELATED" label="关联方 RELATED" /><el-option value="OTHER" label="其他 OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="分级">
          <el-select v-model="custForm.levelAbc" style="width: 100%">
            <el-option value="A" /><el-option value="B" /><el-option value="C" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="custDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createCustomer">创建</el-button>
      </div>
    </el-dialog>

    <el-dialog title="新建物料" :visible.sync="matDialog" width="480px">
      <el-form label-width="100px" size="small">
        <el-form-item label="名称" required><el-input v-model.trim="matForm.materialName" /></el-form-item>
        <el-form-item label="规格"><el-input v-model.trim="matForm.spec" /></el-form-item>
        <el-form-item label="类别" required>
          <el-select v-model="matForm.category" style="width: 100%">
            <el-option value="STD" label="标准件" /><el-option value="FAB" label="钣金件" /><el-option value="PURCHASE" label="外购" /><el-option value="ASSY" label="组装" />
          </el-select>
        </el-form-item>
        <el-form-item label="计量单位" required>
          <el-select v-model="matForm.unit" style="width: 100%">
            <el-option v-for="u in units" :key="u" :value="u" :label="u" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="matDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="createMaterial">创建</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import http from '@/api/request'
import { showError } from '@/api/request'
import { PtTable, PtStatusTag } from '@/components/pt'

/** E-20 受控计量单位 8 种（FR-PLM-15 同口径） */
const UNITS = ['PCS', 'M', 'M2', 'KG', 'T', 'L', 'SET', 'ROOT']

export default {
  name: 'MdmConsole',
  components: { PtTable, PtStatusTag },
  data() {
    return {
custCols: [
        { prop: 'customerCode', label: '编码', width: 100 },
        { prop: 'customerName', label: '规范全称', minWidth: 220 },
        { prop: 'shortName', label: '简称', width: 120 },
        { prop: 'level', label: '层级', width: 100 },
        { prop: 'customerType', label: '类型', width: 110 },
        { prop: 'levelAbc', label: '分级', width: 80 },
        { prop: 'status', label: '状态', width: 90 }
      ],
      tab: 'customers',
      units: UNITS,
      customers: [], custLoading: false, custDialog: false, custForm: {},
      mdProjects: [], projLoading: false,
      matDialog: false, matForm: {}
    }
  },
  mounted() {
    this.loadCustomers()
    this.loadProjects()
  },
  methods: {
    loadCustomers() {
      this.custLoading = true
      http.get('/crm/customers', { params: { pageNum: 1, pageSize: 50 } })
        .then(body => { this.customers = body.data.records || [] })
        .catch(showError).finally(() => { this.custLoading = false })
    },
    loadProjects() {
      this.projLoading = true
      http.get('/mdm/projects', { params: { pageNum: 1, pageSize: 50 } })
        .then(body => { this.mdProjects = body.data.records || [] })
        .catch(showError).finally(() => { this.projLoading = false })
    },
    createCustomer() {
      http.post('/mdm/customers', this.custForm).then(body => {
        this.$message.success('客户已建档 ' + (body.data.customerCode || body.data))
        this.custDialog = false
        this.loadCustomers()
      }).catch(showError)
    },
    createMaterial() {
      http.post('/mdm/materials', this.matForm).then(body => {
        this.$message.success('物料已建档 id=' + body.data)
        this.matDialog = false
      }).catch(showError)
    }
  }
}
</script>
