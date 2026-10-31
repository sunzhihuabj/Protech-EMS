<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div class="pl-toolbar">
        <el-input v-model="query.keyword" placeholder="项目名称" size="small" style="width: 200px" clearable @keyup.enter.native="load" />
        <el-select v-model="query.status" size="small" style="width: 130px" clearable placeholder="状态" @change="load">
          <el-option v-for="s in statuses" :key="s.value" :value="s.value" :label="s.label" />
        </el-select>
        <el-button size="small" type="primary" icon="el-icon-search" @click="load">查询</el-button>
        <div class="spacer"></div>
        <el-button v-permission="'project:create'" size="small" type="primary" icon="el-icon-plus" @click="openCreate">手动立项</el-button>
      </div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="total"
                :page.sync="page" aria-label="项目台账" empty-type="no-result"
                empty-label="项目" empty-action-text="新建项目" @empty-action="dialog = true"
                @page-change="load">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status" :dict="statusDict" />
        </template>
        <template slot="actions" slot-scope="{ row }">
          <el-button size="mini" type="text" @click.stop="openDetail(row)">任务/甘特</el-button>
          <el-button v-permission="'project:eco:create'" size="mini" type="text" @click.stop="openEco(row)">发起 ECO</el-button>
          <el-button size="mini" type="text" @click.stop="loadEcos(row)">ECO 列表</el-button>
        </template>
      </pt-table>
      <el-pagination background layout="total, prev, pager, next" :total="total"
                     :page-size="query.pageSize" :current-page.sync="query.pageNum" @current-change="load" />
    </el-card>

    <!-- 任务树 + 简易甘特 -->
    <el-dialog :title="'任务与甘特：' + (current && current.projectName)" :visible.sync="detailDialog" width="960px">
      <div class="pl-toolbar">
        <el-button size="mini" @click="refreshDetail">刷新</el-button>
        <span class="muted">前置未完成任务时「开工」被拒（BR-PRJ-02）；调整结束日期自动顺延后续任务（FR-PRJ-10）</span>
      </div>
      <el-table :data="tasks" v-loading="detailLoading" size="mini" row-key="id" :tree-props="{ children: 'children' }">
        <el-table-column prop="wbsCode" label="WBS" width="70" />
        <el-table-column prop="taskName" label="任务" min-width="180" />
        <el-table-column prop="planStart" label="开始" width="95" />
        <el-table-column prop="planEnd" label="结束" width="95" />
        <el-table-column prop="status" label="状态" width="90">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="row.status === 'DONE' ? 'success' : row.status === 'DELAYED' ? 'danger' : row.status === 'DOING' ? '' : 'info'">
              {{ taskStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template slot-scope="{ row }">
            <el-button v-if="row.status === 'TODO' || row.status === 'DELAYED'" size="mini" type="text" @click="startTask(row)">开工</el-button>
            <el-button v-if="row.status === 'DOING'" size="mini" type="text" @click="finishTask(row)">完工</el-button>
            <el-button v-permission="'project:task:adjust_date'" v-if="row.status !== 'DONE'" size="mini" type="text" @click="adjustDate(row)">调日期</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- CSS 甘特条（S2 数据结构版；拖拽甘特库 S8 优化批次接入） -->
      <div class="gantt" v-if="ganttSpan > 0">
        <div v-for="t in tasks" :key="'g' + t.id" class="gantt-row">
          <span class="gantt-label">{{ t.wbsCode }} {{ t.taskName }}</span>
          <div class="gantt-track">
            <div class="gantt-bar" :class="'st-' + t.status"
                 :style="{ left: offsetPct(t) + '%', width: widthPct(t) + '%' }"></div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 手动立项 -->
    <el-dialog title="手动立项（FR-PRJ-02）" :visible.sync="dialog" width="560px">
      <el-form label-width="110px" size="small">
        <el-form-item label="项目档案" required>
          <el-select v-model="form.mdProjectId" filterable style="width: 100%" placeholder="从项目档案选择（FR-PRJ-03 双号关联）">
            <el-option v-for="p in mdProjects" :key="p.id" :value="p.id" :label="`${p.projectCode} ${p.stdName}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目名称" required><el-input v-model.trim="form.projectName" /></el-form-item>
        <el-form-item label="项目经理" required>
          <el-select v-model="form.managerId" filterable style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.realName" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板" required>
          <el-select v-model="templateId" style="width: 100%">
            <el-option v-for="t in templates" :key="t.id" :value="t.id" :label="t.templateName" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划起止" required>
          <el-date-picker v-model="form.planStart" type="date" value-format="yyyy-MM-dd" style="width: 48%" />
          <el-date-picker v-model="form.planEnd" type="date" value-format="yyyy-MM-dd" style="width: 48%" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="save">立项（生成任务树）</el-button>
      </span>
    </el-dialog>

    <!-- ECO -->
    <el-dialog title="发起 ECO（BR-09：旧料处置方案必填）" :visible.sync="ecoDialog" width="560px">
      <el-form label-width="120px" size="small">
        <el-form-item label="变更原因" required><el-input v-model="ecoForm.changeReason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="变更内容" required><el-input v-model="ecoForm.changeContent" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="影响分析"><el-input v-model="ecoForm.impactAnalysis" type="textarea" :rows="2" placeholder="采购/生产/进度影响" /></el-form-item>
        <el-form-item label="旧料处置方案" required>
          <el-input v-model="ecoForm.oldDisposalPlan" type="textarea" :rows="2"
                    placeholder="≥10 字：优先领用/工程售后利用/改制/折价出售/报废/客户消化" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="ecoDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveEco">提交（生成 ECN）</el-button>
      </span>
    </el-dialog>

    <!-- ECO 列表 -->
    <el-dialog title="ECO 列表" :visible.sync="ecoListDialog" width="720px">
      <div v-if="ecos.length === 0" class="muted">该项目暂无 ECO</div>
      <el-table v-else :data="ecos" size="mini">
        <el-table-column prop="ecoNo" label="ECN" width="140" />
        <el-table-column prop="changeReason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="oldDisposalPlan" label="旧料处置" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="110">
          <template slot-scope="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" v-permission="'project:eco:approve'" size="mini" type="text"
                       @click="approveEco(row)">批准生效</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import { showError } from '@/api/request'
import { PtTable, PtStatusTag } from '@/components/pt'

export default {
  name: 'ProjectsPage',
  components: { PtTable, PtStatusTag },
  data() {
    return {
columns: [
        { prop: 'projectNo', label: '执行号', width: 150 },
        { prop: 'projectName', label: '项目名称', minWidth: 220 },
        { prop: 'status', label: '状态', width: 110 },
        { prop: 'planStart', label: '计划开始', width: 112 },
        { prop: 'planEnd', label: '计划结束', width: 112 }
      ],
      statusDict: 'CREATED:已建档,SIGNED_PENDING:签约待执行,IN_PROGRESS:进行中,COMPLETED:已完工,CLOSED:已关闭',
      page: { pageNum: 1, pageSize: 20 },
      records: [], total: 0, loading: false,
      query: { pageNum: 1, pageSize: 20, keyword: '', status: '' },
      statuses: [
        { value: 'NOT_STARTED', label: '未启动' }, { value: 'IN_PROGRESS', label: '进行中' },
        { value: 'COMPLETED', label: '已完成' }, { value: 'CLOSED', label: '已关闭' },
        { value: 'PAUSED', label: '已暂停' }
      ],
      dialog: false, form: {}, templates: [], templateId: null, mdProjects: [], users: [],
      detailDialog: false, detailLoading: false, tasks: [], current: null,
      ecoDialog: false, ecoForm: {}, ecoTarget: null,
      ecoListDialog: false, ecos: []
    }
  },
  computed: {
    ganttSpan() {
      if (!this.tasks.length) return 0
      const starts = this.tasks.map(t => this.day(t.planStart))
      const ends = this.tasks.map(t => this.day(t.planEnd))
      return Math.max(...ends) - Math.min(...starts) + 1
    }
  },
  mounted() {
    this.load()
    this.loadTemplates()
    this.loadMdProjects()
  },
  methods: {
    statusLabel(status) {
      const found = this.statuses.find(s => s.value === status)
      return found ? found.label : status
    },
    taskStatusLabel(status) {
      return { TODO: '未开始', DOING: '进行中', DONE: '已完成', DELAYED: '已延期',
               PAUSED: '暂停', CANCELLED: '取消', PENDING_APPROVAL: '待审批' }[status] || status
    },
    day(dateStr) {
      return new Date(String(dateStr).replace(/-/g, '/')).getTime()
    },
    offsetPct(task) {
      const min = Math.min(...this.tasks.map(t => this.day(t.planStart)))
      return (this.day(task.planStart) - min) / 86400000 / this.ganttSpan * 100
    },
    widthPct(task) {
      const days = (this.day(task.planEnd) - this.day(task.planStart)) / 86400000 + 1
      return Math.max(days / this.ganttSpan * 100, 1.5)
    },
    load() {
      this.loading = true
      this.$http.get('/project', { params: this.query }).then(body => {
        this.records = (body.data && body.data.records) || []
        this.total = (body.data && body.data.total) || 0
      }).catch(showError).finally(() => { this.loading = false })
    },
    loadTemplates() {
      this.$http.get('/project/templates').then(body => { this.templates = body.data || [] }).catch(() => {})
    },
    loadMdProjects() {
      this.$http.get('/mdm/projects', { params: { pageNum: 1, pageSize: 100 } }).then(body => {
        this.mdProjects = (body.data && body.data.records) || []
      }).catch(() => {})
      this.$http.get('/org/users', { params: { pageNum: 1, pageSize: 100 } }).then(body => {
        this.users = (body.data && body.data.records) || []
      }).catch(() => {})
    },
    openCreate() {
      this.form = {}
      this.templateId = null
      this.dialog = true
    },
    save() {
      if (!this.form.mdProjectId || !this.form.projectName || !this.form.managerId || !this.form.planStart) {
        this.$message.warning('档案/名称/经理/计划起止必填')
        return
      }
      this.$http.post(`/project?templateId=${this.templateId || ''}`, this.form).then(body => {
        this.$message.success(`立项成功（执行号见列表），任务树已按模板生成`)
        this.dialog = false
        this.load()
      }).catch(showError)
    },
    openDetail(row) {
      this.current = row
      this.detailDialog = true
      this.refreshDetail()
    },
    refreshDetail() {
      this.detailLoading = true
      this.$http.get(`/project/${this.current.id}/tasks`).then(body => {
        this.tasks = this.buildTree(body.data || [])
      }).catch(showError).finally(() => { this.detailLoading = false })
    },
    buildTree(flat) {
      const map = {}
      const roots = []
      flat.forEach(t => { map[t.id] = { ...t, children: [] } })
      flat.forEach(t => {
        if (t.parentId && map[t.parentId]) {
          map[t.parentId].children.push(map[t.id])
        } else {
          roots.push(map[t.id])
        }
      })
      return roots
    },
    startTask(row) {
      this.$http.post(`/project/tasks/${row.id}/start`).then(() => {
        this.$message.success('已开工')
        this.refreshDetail()
      }).catch(showError)
    },
    finishTask(row) {
      this.$prompt('完成说明（若模板要求产出物，需先在附件上传）', `完工：${row.taskName}`, {
        inputType: 'textarea'
      }).then(({ value }) => {
        return this.$http.post(`/project/tasks/${row.id}/finish`, { finishNote: value || '' })
      }).then(() => {
        this.$message.success('已提交完工' )
        this.refreshDetail()
      }).catch(e => { if (e !== 'cancel') showError(e) })
    },
    adjustDate(row) {
      this.$prompt('新结束日期（后续依赖任务自动顺延）', `调整：${row.taskName}`, {
        inputValue: row.planEnd,
        inputPattern: /^\d{4}-\d{2}-\d{2}$/, inputErrorMessage: '格式 yyyy-MM-dd'
      }).then(({ value }) => {
        return this.$http.post(`/project/tasks/${row.id}/adjust-date`, { planEnd: value })
      }).then(body => {
        this.$message.success(`已调整，顺延 ${body.data} 个后续任务`)
        this.refreshDetail()
      }).catch(e => { if (e !== 'cancel') showError(e) })
    },
    openEco(row) {
      this.ecoTarget = row
      this.ecoForm = {}
      this.ecoDialog = true
    },
    saveEco() {
      this.$http.post(`/project/${this.ecoTarget.id}/ecos`, this.ecoForm).then(() => {
        this.$message.success('ECO 已提交（ECN 编号生成），待审批')
        this.ecoDialog = false
      }).catch(showError)
    },
    loadEcos(row) {
      this.$http.get(`/project/${row.id}/ecos`).then(body => {
        this.ecos = body.data || []
        this.ecoListDialog = true
      }).catch(showError)
    },
    approveEco(row) {
      this.$http.post(`/project/ecos/${row.id}/approve`, { approved: true }).then(() => {
        this.$message.success('ECO 已批准生效')
        this.loadEcos(this.ecoTarget || row)
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.muted { color: #98a5b1; font-size: 12px; }
.gantt { margin-top: 16px; border-top: 1px solid #eee; padding-top: 8px; }
.gantt-row { display: flex; align-items: center; margin-bottom: 4px; }
.gantt-label { width: 220px; font-size: 12px; color: #556; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gantt-track { flex: 1; height: 14px; background: #f5f7f9; border-radius: 4px; position: relative; }
.gantt-bar { position: absolute; top: 2px; height: 10px; border-radius: 4px; background: #7fa6b5; }
.gantt-bar.st-DONE { background: #67c23a; }
.gantt-bar.st-DOING { background: #409eff; }
.gantt-bar.st-DELAYED { background: #e5484d; }
</style>
