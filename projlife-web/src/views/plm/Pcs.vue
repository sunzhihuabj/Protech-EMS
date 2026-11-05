<template>
  <!-- FR-PCS-01~11：PCS 工艺（过程控制文件 + 工艺路线 + 覆盖度矩阵，SPRINT-UID-R17 接线） -->
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <!-- ① 工艺文件台账 -->
      <el-tab-pane label="工艺文件" name="docs">
        <el-card shadow="never" :body-style="{ padding: '16px' }">
          <div class="pl-toolbar">
            <el-select v-model="query.productLine" size="small" style="width: 150px" clearable placeholder="产品线" @change="onFilter">
              <el-option v-for="l in productLines" :key="l" :value="l" :label="lineLabel(l)" />
            </el-select>
            <el-select v-model="query.status" size="small" style="width: 130px" clearable placeholder="状态" @change="onFilter">
              <el-option value="DRAFT" label="草稿" />
              <el-option value="PUBLISHED" label="已发布" />
              <el-option value="PAUSED" label="已暂停" />
              <el-option value="ARCHIVED" label="已归档" />
            </el-select>
            <el-button size="small" type="primary" icon="el-icon-search" @click="onFilter">查询</el-button>
            <div class="spacer"></div>
            <el-button v-permission="'pcs:manage'" size="small" type="primary" icon="el-icon-plus" @click="openCreate">新建工艺文件</el-button>
          </div>
          <pt-table :columns="docCols" :data="rows" :loading="loading"
                    :total="total" :page.sync="page"
                    @page-change="onPage"
                    aria-label="PCS 工艺文件台账" empty-type="no-data" empty-label="工艺文件">
            <template slot="cell-status" slot-scope="{ row }">
              <pt-status-tag :value="row.status || 'DRAFT'" :dict="'DRAFT:草稿,PUBLISHED:已发布,PAUSED:已暂停,ARCHIVED:已归档'" />
            </template>
            <template slot="cell-isTemp" slot-scope="{ row }">
              <span>{{ row.isTemp === 1 ? '临时（样机）' : '正式' }}</span>
            </template>
            <template slot="actions" slot-scope="{ row }">
              <el-button v-if="row.status === 'DRAFT'" v-permission="'pcs:manage'" size="mini" type="text" @click="publish(row)">发布</el-button>
              <el-button v-if="row.status === 'PUBLISHED'" v-permission="'pcs:manage'" size="mini" type="text" @click="pause(row)">暂停</el-button>
            </template>
          </pt-table>
        </el-card>
      </el-tab-pane>
      <!-- ② 工艺路线（创建后回显步骤） -->
      <el-tab-pane label="工艺路线" name="routes">
        <el-card shadow="never" :body-style="{ padding: '16px' }">
          <div class="pl-toolbar">
            <span class="muted">为已发布 BOM 版本定义工序路线（标准工时 / 关键工序 / 是否需检）</span>
            <div class="spacer"></div>
            <el-button v-permission="'pcs:manage'" size="small" type="primary" icon="el-icon-plus" @click="routeDialog = true">新建工艺路线</el-button>
          </div>
          <el-empty v-if="!lastRoute" description="尚无本次会话创建的路线——创建后在此回显步骤明细" />
          <div v-else>
            <div class="route-head">
              <span>路线 #{{ lastRoute.routeId }} · {{ lastRoute.routeName }}</span>
              <span class="muted">（共 {{ lastRoute.steps.length }} 道工序）</span>
            </div>
            <el-table :data="lastRoute.steps" size="mini" border>
              <el-table-column prop="stepSeq" label="序号" width="70" align="center" />
              <el-table-column prop="stepName" label="工序名称" min-width="160" />
              <el-table-column prop="standardHours" label="标准工时(h)" width="110" align="right" />
              <el-table-column label="关键工序" width="90" align="center">
                <template slot-scope="{ row }">{{ row.isKey === 1 ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="需检验" width="90" align="center">
                <template slot-scope="{ row }">{{ row.needInspection === 1 ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="description" label="说明" min-width="140" />
            </el-table>
          </div>
        </el-card>
      </el-tab-pane>
      <!-- ③ 覆盖度矩阵（FR-PCS-11） -->
      <el-tab-pane label="覆盖度矩阵" name="coverage">
        <el-card shadow="never" :body-style="{ padding: '16px' }">
          <div class="pl-toolbar">
            <span class="muted">已发布 BOM × 已发布工艺文件 覆盖度（FR-PCS-11）</span>
            <div class="spacer"></div>
            <el-button size="small" icon="el-icon-refresh" @click="loadCoverage">刷新</el-button>
          </div>
          <el-table :data="coverage" size="mini" border v-loading="covLoading">
            <el-table-column label="产品线" width="140">
              <template slot-scope="{ row }">{{ lineLabel(row.productLine) }}</template>
            </el-table-column>
            <el-table-column prop="bomCount" label="已发布 BOM" width="110" align="right" />
            <el-table-column prop="docCount" label="已发布工艺文件" width="130" align="right" />
            <el-table-column label="覆盖率" width="180">
              <template slot-scope="{ row }">
                <el-progress :percentage="Number(row.coveragePct) || 0" :stroke-width="12" :color="covColor(row.coveragePct)" />
              </template>
            </el-table-column>
            <el-table-column label="判定" width="110" align="center">
              <template slot-scope="{ row }">
                <el-tag size="mini" :type="covTagType(row.verdict)">{{ row.verdict }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建工艺文件弹窗 -->
    <el-dialog title="新建工艺文件（FR-PCS-01，编码自动取号）" :visible.sync="createDialog" width="520px">
      <el-form label-width="100px" size="small">
        <el-form-item label="文件名称" required>
          <el-input v-model.trim="form.docName" placeholder="如：LADDER 塔筒焊接作业指导书" />
        </el-form-item>
        <el-form-item label="文件类型" required>
          <el-select v-model="form.docType" style="width: 100%">
            <el-option value="作业指导书" label="作业指导书" />
            <el-option value="工艺流程" label="工艺流程" />
            <el-option value="PFMEA" label="PFMEA" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品线" required>
          <el-select v-model="form.productLine" style="width: 100%">
            <el-option v-for="l in productLines" :key="l" :value="l" :label="lineLabel(l)" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model.trim="form.version" placeholder="如 V1.0" style="width: 200px" />
        </el-form-item>
        <el-form-item label="适用 BOM">
          <el-input-number v-model="form.bomHeaderId" :min="1" controls-position="right" placeholder="BOM 头 id" style="width: 200px" />
          <span class="muted" style="margin-left: 8px">BOM 头 id（可在 BOM 台账查询）</span>
        </el-form-item>
        <el-form-item label="临时文件">
          <el-switch v-model="form.isTempBool" active-text="样机阶段临时文件" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="createDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doCreate">保存（草稿）</el-button>
      </div>
    </el-dialog>

    <!-- 新建工艺路线弹窗 -->
    <el-dialog title="新建工艺路线（FR-PCS-06）" :visible.sync="routeDialog" width="680px">
      <el-form label-width="100px" size="small">
        <el-form-item label="路线名称" required>
          <el-input v-model.trim="routeForm.routeName" placeholder="如：LADDER 主结构焊接路线" />
        </el-form-item>
        <el-form-item label="BOM 头 id" required>
          <el-input-number v-model="routeForm.bomHeaderId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
      </el-form>
      <div class="steps-head">
        <span>工序步骤</span>
        <el-button size="mini" icon="el-icon-plus" @click="addStep">添加工序</el-button>
      </div>
      <el-table :data="routeForm.steps" size="mini" border>
        <el-table-column label="工序名称" min-width="150">
          <template slot-scope="{ row }"><el-input v-model.trim="row.stepName" size="mini" placeholder="工序名" /></template>
        </el-table-column>
        <el-table-column label="标准工时(h)" width="120">
          <template slot-scope="{ row }"><el-input-number v-model="row.standardHours" :min="0" :precision="2" :controls="false" size="mini" style="width: 100%" /></template>
        </el-table-column>
        <el-table-column label="关键工序" width="80" align="center">
          <template slot-scope="{ row }"><el-checkbox v-model="row.isKey" /></template>
        </el-table-column>
        <el-table-column label="需检验" width="80" align="center">
          <template slot-scope="{ row }"><el-checkbox v-model="row.needInspection" /></template>
        </el-table-column>
        <el-table-column label="" width="60" align="center">
          <template slot-scope="{ $index }">
            <el-button size="mini" type="text" class="danger-text" @click="routeForm.steps.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div slot="footer">
        <el-button size="small" @click="routeDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doCreateRoute">保存路线</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { PtTable, PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'

export default {
  name: 'PlmPcs',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      tab: 'docs',
      productLines: ['LADDER', 'CLIMBER', 'CLIMB', 'RAIL', 'CCTV', 'FIRE', 'COVER', 'OTHER'],
      query: { pageNum: 1, pageSize: 20, productLine: '', status: '' },
      page: { pageNum: 1, pageSize: 20 },
      rows: [], loading: false, total: 0,
      docCols: [
        { prop: 'docNo', label: '文件编号', width: 150 },
        { prop: 'docName', label: '名称', minWidth: 180 },
        { prop: 'docType', label: '类型', width: 120 },
        { prop: 'productLine', label: '产品线', width: 110 },
        { prop: 'version', label: '版本', width: 90 },
        { prop: 'isTemp', label: '性质', width: 110 },
        { prop: 'status', label: '状态', width: 100 },
        { prop: 'publishedAt', label: '发布时间', width: 160 }
      ],
      createDialog: false,
      form: { docName: '', docType: '作业指导书', productLine: 'LADDER', version: 'V1.0', bomHeaderId: undefined, isTempBool: false },
      routeDialog: false,
      routeForm: { routeName: '', bomHeaderId: undefined, steps: [] },
      lastRoute: null,
      coverage: [], covLoading: false
    }
  },
  mounted() {
    this.load()
    this.loadCoverage()
  },
  methods: {
    lineLabel(l) {
      const m = { LADDER: '梯类', CLIMBER: '登塔防坠落', CLIMB: '攀登设备', RAIL: '轨道', CCTV: '监控', FIRE: '消防', COVER: '防护罩', OTHER: '其他' }
      return m[l] || l
    },
    onPage(p) { this.query.pageNum = p.pageNum; this.query.pageSize = p.pageSize; this.load() },
    onFilter() { this.query.pageNum = 1; this.page.pageNum = 1; this.load() },
    load() {
      this.loading = true
      const params = { pageNum: this.query.pageNum, pageSize: this.query.pageSize }
      if (this.query.productLine) params.productLine = this.query.productLine
      if (this.query.status) params.status = this.query.status
      this.$http.get('/plm/pcs/docs', { params }).then(body => {
        const pageData = body.data || {}
        this.rows = pageData.records || []
        this.total = pageData.total || 0
      }).catch(showError).finally(() => { this.loading = false })
    },
    openCreate() {
      this.form = { docName: '', docType: '作业指导书', productLine: this.query.productLine || 'LADDER', version: 'V1.0', bomHeaderId: undefined, isTempBool: false }
      this.createDialog = true
    },
    doCreate() {
      if (!this.form.docName) { this.$message.warning('文件名称必填'); return }
      if (!this.form.productLine) { this.$message.warning('产品线必填'); return }
      const payload = {
        docName: this.form.docName,
        docType: this.form.docType,
        productLine: this.form.productLine,
        version: this.form.version || 'V1.0',
        isTemp: this.form.isTempBool ? 1 : 0
      }
      if (this.form.bomHeaderId) payload.bomHeaderId = this.form.bomHeaderId
      this.$http.post('/plm/pcs/docs', payload).then(() => {
        this.$message.success('工艺文件已保存（草稿）')
        this.createDialog = false
        this.load()
      }).catch(showError)
    },
    publish(row) {
      this.$http.post(`/plm/pcs/docs/${row.id}/publish`).then(() => {
        this.$message.success('已发布')
        this.load()
      }).catch(showError)
    },
    pause(row) {
      this.$http.post(`/plm/pcs/docs/${row.id}/pause`).then(() => {
        this.$message.success('已暂停')
        this.load()
      }).catch(showError)
    },
    addStep() {
      this.routeForm.steps.push({ stepName: '', standardHours: 0, isKey: false, needInspection: false })
    },
    doCreateRoute() {
      if (!this.routeForm.routeName) { this.$message.warning('路线名称必填'); return }
      if (!this.routeForm.bomHeaderId) { this.$message.warning('BOM 头 id 必填'); return }
      if (!this.routeForm.steps.length) { this.$message.warning('至少一道工序'); return }
      const payload = {
        bomHeaderId: this.routeForm.bomHeaderId,
        routeName: this.routeForm.routeName,
        steps: this.routeForm.steps.map(s => ({
          stepName: s.stepName, standardHours: s.standardHours,
          isKey: !!s.isKey, needInspection: !!s.needInspection
        }))
      }
      this.$http.post('/plm/pcs/routes', payload).then(body => {
        const routeId = body.data
        this.$message.success(`工艺路线已保存（#${routeId}）`)
        this.routeDialog = false
        // 回显：后端无路线列表端点，用返回 id 拉步骤明细（契约限制，见 SPRINT-UID-R17）
        this.$http.get(`/plm/pcs/routes/${routeId}/steps`).then(b2 => {
          this.lastRoute = { routeId, routeName: this.routeForm.routeName, steps: b2.data || [] }
          this.tab = 'routes'
        }).catch(showError)
      }).catch(showError)
    },
    loadCoverage() {
      this.covLoading = true
      this.$http.get('/plm/pcs/coverage').then(body => {
        this.coverage = body.data || []
      }).catch(showError).finally(() => { this.covLoading = false })
    },
    covColor(pct) {
      const v = Number(pct) || 0
      if (v === 0) return 'var(--danger)'
      if (v < 5) return 'var(--danger)'
      if (v < 15) return 'var(--warn)'
      return 'var(--ok)'
    },
    covTagType(verdict) {
      if (verdict === '零覆盖' || verdict === '严重不足') return 'danger'
      if (verdict === '偏低') return 'warning'
      return 'success'
    }
  }
}
</script>

<style scoped>
.muted { color: var(--ink-3); font-size: 12px; }
.spacer { flex: 1; }
.route-head { margin: 8px 0; font-weight: 600; }
.steps-head { display: flex; align-items: center; justify-content: space-between; margin: 4px 0 8px; }
.danger-text { color: var(--danger, #f56c6c); }
</style>
