<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div class="pl-toolbar">
        <el-select v-model="query.bomForm" size="small" style="width: 110px" clearable placeholder="形态" @change="load">
          <el-option value="A" label="A 标准" /><el-option value="B" label="B 参数化" /><el-option value="C" label="C 工程" />
        </el-select>
        <el-select v-model="query.bomView" size="small" style="width: 110px" clearable placeholder="视图" @change="load">
          <el-option value="EBOM" label="EBOM 设计" /><el-option value="MBOM" label="MBOM 制造" /><el-option value="SBOM" label="SBOM 服务" />
        </el-select>
        <el-button size="small" type="primary" icon="el-icon-search" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button v-permission="'bom:create'" size="small" type="primary" icon="el-icon-plus" @click="openCreate">新建 BOM</el-button>
      </div>
      <el-table :data="records" v-loading="loading" size="small">
        <el-table-column prop="bomNo" label="BOM 编号" min-width="220" />
        <el-table-column prop="bomForm" label="形态" width="90">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="row.bomForm === 'A' ? 'success' : row.bomForm === 'B' ? '' : 'warning'">
              {{ { A: '标准', B: '参数化', C: '工程' }[row.bomForm] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bomView" label="视图" width="80" />
        <el-table-column prop="productModel" label="型号" width="120" />
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column label="现行" width="70">
          <template slot-scope="{ row }">
            <el-tag v-if="row.isCurrent" size="mini" type="success">现行</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="操作" min-width="300">
          <template slot-scope="{ row }">
            <el-button v-if="row.status === 'DRAFT'" size="mini" type="text" @click="addLine(row)">加行</el-button>
            <el-button v-if="row.status === 'DRAFT'" size="mini" type="text" @click="validate(row)">13 条校验</el-button>
            <el-button v-if="row.status === 'DRAFT'" size="mini" type="text" @click="release(row)">发布</el-button>
            <el-button v-if="row.bomView === 'EBOM' && row.status === 'RELEASED'" size="mini" type="text" @click="convert(row)">转 SBOM</el-button>
            <el-button v-if="row.bomView === 'EBOM' && row.status === 'RELEASED'" size="mini" type="text" @click="derive(row)">派生项目 BOM</el-button>
            <el-button size="mini" type="text" @click="ecn(row)">ECN</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination background layout="total, prev, pager, next" :total="total"
                     :page-size="query.pageSize" :current-page.sync="query.pageNum" @current-change="load" />
    </el-card>

    <el-dialog title="新建 BOM（FR-PLM-11）" :visible.sync="dialog" width="520px">
      <el-form label-width="100px" size="small">
        <el-form-item label="形态（E-17）" required>
          <el-select v-model="form.bomForm" style="width: 100%" @change="form.bomView = ''">
            <el-option value="A" label="A 标准产品" /><el-option value="B" label="B 参数化定制" /><el-option value="C" label="C 系统工程" />
          </el-select>
        </el-form-item>
        <el-form-item label="视图（E-18）" required>
          <el-select v-model="form.bomView" style="width: 100%">
            <el-option v-for="v in viewsFor(form.bomForm)" :key="v.value" :value="v.value" :label="v.label" />
          </el-select>
          <div v-if="form.bomForm === 'C' && form.bomView === 'MBOM'" class="not-applicable">
            不适用（工程物料无制造过程，BR-PLM-07）
          </div>
        </el-form-item>
        <el-form-item label="产品线" required>
          <el-select v-model="form.productLine" style="width: 100%">
            <el-option v-for="p in ['LADDER', 'CLIMBER', 'CLIMB', 'RAIL', 'CCTV', 'FIRE', 'COVER', 'OTHER']" :key="p" :value="p" :label="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="型号" required><el-input v-model.trim="form.productModel" placeholder="如 TLDL-300" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="save">保存</el-button>
      </span>
    </el-dialog>

    <el-dialog title="BOM 行（FR-PLM-12/13）" :visible.sync="lineDialog" width="760px">
      <el-table :data="lines" size="mini" max-height="280">
        <el-table-column prop="lineNo" label="层级" width="70" />
        <el-table-column prop="materialCode" label="物料编码" width="110" />
        <el-table-column prop="qtyPerUnit" label="用量" width="80" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="操作" width="80">
          <template slot-scope="{ row }">
            <el-button size="mini" type="text" @click="removeLine(row)">删行</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-divider />
      <el-form inline size="small">
        <el-form-item><el-select v-model="lineForm.materialId" filterable placeholder="物料（必选，禁手输）" style="width: 200px">
          <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="`${m.materialCode} ${m.materialName}`" />
        </el-select></el-form-item>
        <el-form-item><el-input v-model.trim="lineForm.lineNo" placeholder="层级 1 / 1-1" style="width: 100px" /></el-form-item>
        <el-form-item><el-input-number v-model="lineForm.qtyPerUnit" :min="0.001" style="width: 120px" /></el-form-item>
        <el-form-item><el-input v-model.trim="lineForm.unit" placeholder="单位(PCS/M/KG)" style="width: 110px" /></el-form-item>
        <el-form-item><el-button size="small" type="primary" @click="saveLine">加行</el-button></el-form-item>
      </el-form>
      <div v-if="issues.length" class="issues">
        <div v-for="(iss, idx) in issues" :key="idx">
          <el-tag size="mini" :type="iss.level === 'ERROR' ? 'danger' : iss.level === 'WARN' ? 'warning' : 'info'">
            规则{{ iss.rule }} {{ iss.level }}
          </el-tag> {{ iss.message }}
        </div>
      </div>
      <span slot="footer">
        <el-button size="small" @click="lineDialog = false">关闭</el-button>
        <el-button size="small" type="warning" @click="runValidate">跑 13 条校验</el-button>
        <el-button v-permission="'bom:release'" size="small" type="primary" @click="doRelease">发布</el-button>
      </span>
    </el-dialog>

    <el-dialog title="ECN 变更（BR-09：旧料处置必填）" :visible.sync="ecoDialog" width="560px">
      <el-form label-width="120px" size="small">
        <el-form-item label="变更类型" required>
          <el-select v-model="ecoForm.changeType" style="width: 100%">
            <el-option v-for="t in ['设计', '工艺', '替换料', '纠错']" :key="t" :value="t" :label="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因" required><el-input v-model="ecoForm.changeReason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="旧料处置方案" required>
          <el-input v-model="ecoForm.oldDisposalPlan" type="textarea" :rows="2"
                    placeholder="≥10 字：优先领用/改制/退供应商/报废/客户消化 + 说明" />
        </el-form-item>
        <el-form-item label="影响确认">
          <el-checkbox v-model="impactConfirmed">影响清单已确认（未确认拒绝进入审批）</el-checkbox>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="ecoDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveEco">提交（ECN 编号）</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { showError } from '@/api/request'

export default {
  name: 'BomsPage',
  data() {
    return {
      records: [], total: 0, loading: false,
      query: { pageNum: 1, pageSize: 20, bomForm: '', bomView: '' },
      dialog: false, form: {},
      lineDialog: false, lines: [], lineForm: {}, lineTarget: null, issues: [], materials: [],
      ecoDialog: false, ecoForm: {}, impactConfirmed: false, ecoTarget: null
    }
  },
  mounted() {
    this.load()
    this.loadMaterials()
  },
  methods: {
    viewsFor(form) {
      if (form === 'C') {
        return [{ value: 'EBOM', label: 'EBOM 工程物料清单' }, { value: 'SBOM', label: 'SBOM 设备档案' }]
      }
      return [{ value: 'EBOM', label: 'EBOM 设计' }, { value: 'MBOM', label: 'MBOM 制造' }, { value: 'SBOM', label: 'SBOM 服务' }]
    },
    load() {
      this.loading = true
      this.$http.get('/plm/bom', { params: this.query }).then(body => {
        this.records = (body.data && body.data.records) || []
        this.total = (body.data && body.data.total) || 0
      }).catch(showError).finally(() => { this.loading = false })
    },
    loadMaterials() {
      this.$http.get('/mdm/materials', { params: { pageNum: 1, pageSize: 100 } }).then(body => {
        this.materials = (body.data && body.data.records) || []
      }).catch(() => {})
    },
    openCreate() {
      this.form = {}
      this.dialog = true
    },
    save() {
      if (!this.form.bomForm || !this.form.bomView || !this.form.productLine || !this.form.productModel) {
        this.$message.warning('形态/视图/产品线/型号必填')
        return
      }
      if (this.form.bomForm === 'C' && this.form.bomView === 'MBOM') {
        this.$message.warning('C 类 MBOM 不适用（BR-PLM-07）')
        return
      }
      this.$http.post('/plm/bom', this.form).then(() => {
        this.$message.success('BOM 已创建')
        this.dialog = false
        this.load()
      }).catch(showError)
    },
    addLine(row) {
      this.lineTarget = row
      this.lineForm = { qtyPerUnit: 1 }
      this.issues = []
      this.lineDialog = true
      this.$http.get(`/plm/bom/${row.id}/lines`).then(body => { this.lines = body.data || [] }).catch(() => {})
    },
    saveLine() {
      if (!this.lineForm.materialId || !this.lineForm.lineNo || !this.lineForm.unit) {
        this.$message.warning('物料/层级/单位必填')
        return
      }
      this.$http.post(`/plm/bom/${this.lineTarget.id}/lines`, this.lineForm).then(() => {
        this.$message.success('已加行')
        this.$http.get(`/plm/bom/${this.lineTarget.id}/lines`).then(body => { this.lines = body.data || [] })
      }).catch(showError)
    },
    removeLine(row) {
      this.$http.delete(`/plm/bom/lines/${row.id}`).then(() => {
        this.$message.success('已软删（BR-PLM-06）')
        this.$http.get(`/plm/bom/${this.lineTarget.id}/lines`).then(body => { this.lines = body.data || [] })
      }).catch(showError)
    },
    runValidate() {
      this.$http.get(`/plm/bom/${this.lineTarget.id}/validate`).then(body => {
        this.issues = body.data || []
        if (this.issues.length === 0) {
          this.$message.success('13 条校验全部通过')
        }
      }).catch(showError)
    },
    doRelease() {
      this.$http.post(`/plm/bom/${this.lineTarget.id}/release`).then(body => {
        this.$message.success(`已发布（${body.data.bomNo}），警告 ${body.data.warnings} 条`)
        this.lineDialog = false
        this.load()
      }).catch(showError)
    },
    convert(row) {
      this.$http.post(`/plm/bom/${row.id}/convert?targetView=SBOM`).then(body => {
        this.$message.success('已生成 SBOM（受控件口径，A-17）')
        this.load()
      }).catch(showError)
    },
    derive(row) {
      this.$prompt('输入项目档案 id（从主数据页复制）', '派生项目 BOM', { inputPattern: /^\d+$/ })
        .then(({ value }) => this.$http.post(`/plm/bom/${row.id}/derive?mdProjectId=${value}`))
        .then(() => {
          this.$message.success('已派生（差异 0 处，只允许改量/删行/加项目专用行）')
          this.load()
        }).catch(e => { if (e !== 'cancel') showError(e) })
    },
    ecn(row) {
      this.ecoTarget = row
      this.ecoForm = {}
      this.impactConfirmed = false
      this.ecoDialog = true
    },
    saveEco() {
      if (!this.ecoForm.changeType || !this.ecoForm.changeReason) {
        this.$message.warning('变更类型与原因必填')
        return
      }
      this.$http.post(`/plm/bom/${this.ecoTarget.id}/ecn`,
        { ...this.ecoForm, impactConfirmed: this.impactConfirmed ? 1 : 0 }).then(body => {
        this.$message.success(`ECN 已提交（${body.data.changeNo || '编号见列表'}）`)
        this.ecoDialog = false
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.not-applicable { color: #e08700; font-size: 12px; margin-top: 4px; }
.issues { max-height: 160px; overflow: auto; font-size: 12px; color: #556; }
.issues div { margin-bottom: 4px; }
</style>
