<template>
  <div class="pl-page">
    <!-- 头部 -->
    <div class="pl-doc-head">
      <span class="pl-doc-no">{{ project.projectNo || ('#' + id) }}</span>
      <span class="pl-doc-name">{{ project.projectName }}</span>
      <pt-status-tag :value="project.status" :dict="'PLANNED:计划中,ACTIVE:执行中,COMPLETED:已完工,CLOSED:已关闭,SUSPENDED:暂停'" />
      <span class="spacer"></span>
      <span class="muted">进度 {{ project.progressPct == null ? '—' : project.progressPct + '%' }}</span>
      <span class="muted">工期 {{ project.planStart }} ~ {{ project.planEnd }}</span>
    </div>

    <!-- 关键路径（CPM） -->
    <el-card shadow="never" :body-style="{ padding: '16px' }" style="margin-bottom:8px">
      <pt-form-section title="关键路径（CPM：松弛=0 即关键）" :error-count="0">
        <div class="muted" style="margin-bottom:8px">
          总工期 <b>{{ cpm.totalDurationDays == null ? '—' : cpm.totalDurationDays + ' 天' }}</b>
          · 关键任务 <b>{{ criticalCount }}</b>/{{ (cpm.tasks || []).length }}
        </div>
        <el-table :data="cpm.tasks || []" size="small" max-height="260">
          <el-table-column prop="wbsCode" label="WBS" width="110" />
          <el-table-column prop="taskName" label="任务" min-width="200" />
          <el-table-column prop="es" label="最早开始" width="104" />
          <el-table-column prop="ef" label="最早完成" width="104" />
          <el-table-column prop="slack" label="松弛(天)" width="88" align="right" />
          <el-table-column label="关键" width="70">
            <template slot-scope="{ row }">
              <span :class="row.critical ? 'cpm-yes' : 'cpm-no'">{{ row.critical ? '关键' : '—' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </pt-form-section>
    </el-card>

    <!-- 甘特（日刻度，拖拽右缘调整完成日） -->
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <pt-form-section title="WBS 甘特（拖拽条形 → 松手按 project:task:adjust_date 调整完成日）" :error-count="0">
        <div class="gantt-wrap" ref="ganttWrap" @mousemove="onDragMove" @mouseup="onDragEnd" @mouseleave="onDragEnd">
          <div v-for="row in ganttRows" :key="row.task.id" class="gantt-row">
            <div class="gantt-label" :title="row.task.taskName">{{ row.task.wbsCode }} {{ row.task.taskName }}</div>
            <div class="gantt-track">
              <div class="gantt-bar" :class="{ critical: row.critical, dragging: drag && drag.id === row.task.id }"
                   :style="{ left: row.leftPx + 'px', width: row.widthPx + 'px' }"
                   :disabled="!canAdjust"
                   @mousedown="onDragStart($event, row)">
                <span class="gantt-bar-text">{{ row.days }}d{{ row.critical ? ' ★' : '' }}</span>
              </div>
            </div>
          </div>
          <el-empty v-if="!ganttRows.length" description="项目暂无任务（任务计划在 WBS 分解中创建）" :image-size="60" />
        </div>
        <div class="muted" style="margin-top:8px">
          刻度 {{ PX_PER_DAY }}px/天 · 拖拽后按 project:task:adjust_date 持久化并重算 CPM
        </div>
      </pt-form-section>
    </el-card>

    <!-- SPRINT-UID-R16：成员管理 + 门控 + ECO 补齐 -->
    <el-row :gutter="16" style="margin-top: 8px">
      <el-col :span="14">
        <!-- 成员管理 -->
        <el-card shadow="never" :body-style="{ padding: '16px' }" style="margin-bottom: 12px">
          <div slot="header" style="display: flex; justify-content: space-between; align-items: center">
            <b>项目成员（{{ members.length }}）</b>
            <el-button v-permission="'project:member:manage'" size="mini" type="primary" plain @click="showAddMember">添加成员</el-button>
          </div>
          <el-empty v-if="!members.length" description="暂无项目成员" :image-size="50" />
          <el-table v-else :data="members" size="mini">
            <el-table-column prop="userId" label="用户ID" width="90" />
            <el-table-column prop="roleCode" label="角色" width="120">
              <template slot-scope="{ row }">{{ roleDict(row.roleCode) }}</template>
            </el-table-column>
            <el-table-column prop="joinDate" label="加入日" width="120" />
            <el-table-column prop="leaveDate" label="退出日" width="120">
              <template slot-scope="{ row }">{{ row.leaveDate || '—' }}</template>
            </el-table-column>
            <el-table-column label="在册" width="80">
              <template slot-scope="{ row }">
                <pt-status-tag :value="row.isActive === 0 ? 'INACTIVE' : 'ACTIVE'" :dict="'ACTIVE:在册,INACTIVE:已退出'" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template slot-scope="{ row }">
                <el-button v-if="row.isActive !== 0" v-permission="'project:member:manage'" size="mini" type="text"
                           style="color:var(--danger,#f56c6c)" @click="removeMember(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <!-- 门控管理 -->
        <el-card shadow="never" :body-style="{ padding: '16px' }">
          <div slot="header"><b>阶段门控（门控通过 = 放行下一阶段）</b></div>
          <el-empty v-if="!gateTasks.length" description="项目暂无门控任务" :image-size="50" />
          <el-table v-else :data="gateTasks" size="mini">
            <el-table-column prop="wbsCode" label="WBS" width="100" />
            <el-table-column prop="taskName" label="阶段任务" min-width="160" />
            <el-table-column prop="planEnd" label="计划完成日" width="110" />
            <el-table-column label="子任务" width="140">
              <template slot-scope="{ row }">
                <span class="muted">{{ gateInfoOf(row).doneCount }}/{{ gateInfoOf(row).totalChildren }} 完成</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template slot-scope="{ row }">
                <pt-status-tag :value="gateStatusOf(row)" :dict="'PENDING:待通过,PASSED:已通过'" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template slot-scope="{ row }">
                <el-button v-if="gateStatusOf(row) === 'PENDING'" v-permission="'project:gate:review'"
                           size="mini" type="primary" :disabled="!gateInfoOf(row).gateOpen"
                           @click="passGate(row)">通过门控</el-button>
                <span v-else class="muted">已放行</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <!-- ECO 管理（SPRINT-UID-R18：补影响面动作） -->
        <el-card shadow="never" :body-style="{ padding: '16px' }" style="margin-bottom: 12px">
          <div slot="header"><b>ECO 工程变更请求（{{ ecos.length }}）</b></div>
          <el-empty v-if="!ecos.length" description="暂无 ECO" :image-size="50" />
          <el-table v-else :data="ecos" size="mini" stripe>
            <el-table-column prop="ecoNo" label="编号" width="100" />
            <el-table-column prop="changeContent" label="变更内容" min-width="140" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="84">
              <template slot-scope="{ row }">
                <pt-status-tag :value="row.status || 'OPEN'" :dict="'OPEN:待审批,APPROVED:已通过,REJECTED:已驳回'" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="76">
              <template slot-scope="{ row }">
                <el-button size="mini" type="text" @click="showEcoImpact(row)">影响面</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <!-- SPRINT-UID-R18：任务依赖（此前 POST /tasks/{id}/dependencies 无任何前端入口） -->
        <el-card shadow="never" :body-style="{ padding: '16px' }">
          <!-- FR-PRJ-21 CPM 前置关系 -->
          <div slot="header"><b>任务依赖</b></div>
          <el-form size="small" label-width="90px">
            <el-form-item label="本任务ID" required>
              <el-select v-model="depForm.taskId" filterable placeholder="选择后继任务" style="width: 100%">
                <el-option v-for="t in tasks" :key="'dt'+t.id" :value="t.id"
                           :label="(t.wbsCode || '') + ' ' + t.taskName" />
              </el-select>
            </el-form-item>
            <el-form-item label="前置任务" required>
              <el-select v-model="depForm.preTaskId" filterable placeholder="选择前置任务" style="width: 100%">
                <el-option v-for="t in tasks" :key="'pt'+t.id" :value="t.id"
                           :label="(t.wbsCode || '') + ' ' + t.taskName" />
              </el-select>
            </el-form-item>
            <el-form-item label="依赖类型">
              <el-select v-model="depForm.depType" style="width: 100%">
                <el-option value="FS" label="FS 完成-开始（默认）" /><el-option value="SS" label="SS 开始-开始" />
                <el-option value="FF" label="FF 完成-完成" /><el-option value="SF" label="SF 开始-完成" />
              </el-select>
            </el-form-item>
            <el-form-item label="搭接(天)">
              <el-input-number v-model="depForm.lagDays" :precision="0" size="small" style="width: 100%" />
            </el-form-item>
            <el-button v-permission="'project:task:adjust_date'" size="small" type="primary" plain
                       icon="el-icon-connection" @click="addDependency" style="width: 100%">建立依赖</el-button>
          </el-form>
          <p class="muted" style="margin: 8px 0 0">建立后自动参与 CPM 重算；环路由后端 400 阻断。</p>
        </el-card>
        <el-card shadow="never" :body-style="{ padding: '16px' }">
          <div slot="header"><b>说明</b></div>
          <ul style="font-size: 12px; color: var(--ink-2); line-height: 1.8; padding-left: 18px">
            <li>角色枚举：OWNER（项目经理）/LEAD（模块负责人）/MEMBER（执行成员）</li>
            <li>门控节点由任务计划中的 stageFlag=1 任务自动生成</li>
            <li>ECO 未附旧料处置方案 → 后端 BR-09 阻断（HTTP 422）</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>

    <!-- ECO 影响面弹窗（FR-PRJ：变更波及的任务清单） -->
    <el-dialog title="ECO 影响面（波及任务清单）" :visible.sync="ecoImpactDialog" width="520px">
      <div v-if="ecoImpactTarget" class="muted" style="margin-bottom: 8px">
        {{ ecoImpactTarget.ecoNo }} · {{ ecoImpactTarget.changeContent }}
      </div>
      <el-empty v-if="!ecoImpactTasks.length" description="该 ECO 未波及任何任务" :image-size="50" />
      <el-table v-else :data="ecoImpactTasks" size="mini" max-height="320">
        <el-table-column prop="wbsCode" label="WBS" width="100" />
        <el-table-column prop="taskName" label="波及任务" min-width="160" />
        <el-table-column prop="planEnd" label="计划完成" width="104" />
        <el-table-column prop="status" label="状态" width="90">
          <template slot-scope="{ row }">
            <pt-status-tag :value="row.status || 'TODO'" :dict="'TODO:待处理,DOING:进行中,DONE:已完成,DELAYED:延期'" />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 添加成员弹窗 -->
    <el-dialog title="添加项目成员" :visible.sync="addMemberDialog" width="360px">
      <el-form label-width="80px" size="small">
        <el-form-item label="用户ID"><el-input v-model.trim="addMemberForm.userId" placeholder="数字用户 ID" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="addMemberForm.roleCode" style="width: 100%">
            <el-option value="OWNER" label="OWNER（项目经理）" />
            <el-option value="LEAD" label="LEAD（模块负责人）" />
            <el-option value="MEMBER" label="MEMBER（执行成员）" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="addMemberDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doAddMember">添加</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { PtFormSection, PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'

const PX_PER_DAY = 14

export default {
  name: 'ProjectDetail',
  components: { PtFormSection, PtStatusTag },
  data() {
    return {
      project: {},
      tasks: [],
      cpm: {},
      PX_PER_DAY,
      drag: null, // {id, startX, deltaDays}
      // SPRINT-UID-R16：成员/门控/ECO
      members: [],
      gateMap: {}, // {stageTaskId: {approved: boolean, gateNo: string}}
      ecos: [],
      ecoImpactDialog: false, ecoImpactTarget: null, ecoImpactTasks: [],
      depForm: { taskId: null, preTaskId: null, depType: 'FS', lagDays: 0 },
      addMemberDialog: false, addMemberForm: { userId: '', roleCode: 'MEMBER' }
    }
  },
  computed: {
    id() { return this.$route.params.id },
    canAdjust() {
      return (this.$store.state.permissions || []).includes('project:task:adjust_date')
    },
    criticalCount() {
      return (this.cpm.tasks || []).filter(t => t.critical).length
    },
    ganttRows() {
      const withDates = this.tasks.filter(t => t.planStart && t.planEnd)
      if (!withDates.length) return []
      const min = withDates.map(t => t.planStart).sort()[0]
      const cpmById = {}
      ;(this.cpm.tasks || []).forEach(c => { cpmById[c.taskId] = c })
      return withDates.map(t => {
        const c = cpmById[t.id] || {}
        const leftDays = Math.round((new Date(t.planStart) - new Date(min)) / 86400000)
        const days = Math.max(1, Math.round((new Date(t.planEnd) - new Date(t.planStart)) / 86400000) + 1)
        return {
          task: t,
          critical: !!c.critical,
          leftPx: leftDays * PX_PER_DAY,
          widthPx: Math.max(20, days * PX_PER_DAY - 4),
          days
        }
      })
    },
    gateTasks() {
      // 顶层任务视为阶段门控（无 parentId 即顶层；stageFlag/phaseFlag 若有则优先）
      const map = {}
      this.tasks.forEach(t => { if (t.id) map[t.id] = t })
      const topLevel = this.tasks.filter(t => !t.parentId || !map[t.parentId])
      return topLevel.filter(t => t.planStart && t.planEnd).slice(0, 20)
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.$http.get(`/project/${this.id}`).then(b => { this.project = b.data || {} }).catch(showError)
      this.$http.get(`/project/${this.id}/tasks`).then(b => {
        this.tasks = b.data || []
        // 任务加载完后逐个拉门控状态（Promise.all 并发；失败吞掉）
        Promise.all(this.gateTasks.map(t =>
          this.$http.get(`/project/${this.id}/gate/${t.id}`).then(g => ({ id: t.id, ...(g.data || {}) })).catch(() => ({ id: t.id }))
        )).then(results => {
          const m = {}
          results.forEach(r => { m[r.id] = r })
          this.gateMap = m
        })
      }).catch(showError)
      this.$http.get(`/project/${this.id}/critical-path`).then(b => {
        this.cpm = b.data || {}
      }).catch(() => { this.cpm = {} }) // 无任务/环时 CPM 422，甘特仍可看
      // SPRINT-UID-R16：成员 + ECO
      this.$http.get(`/project/${this.id}/members`).then(b => { this.members = b.data || [] }).catch(() => {})
      this.$http.get(`/project/${this.id}/ecos`).then(b => { this.ecos = b.data || [] }).catch(() => {})
    },
    onDragStart(e, row) {
      if (!this.canAdjust) return
      this.drag = { id: row.task.id, task: row.task, startX: e.clientX, deltaDays: 0 }
      e.preventDefault()
    },
    onDragMove(e) {
      if (!this.drag) return
      this.drag.deltaDays = Math.round((e.clientX - this.drag.startX) / PX_PER_DAY)
    },
    onDragEnd() {
      if (!this.drag) return
      const { id, task, deltaDays } = this.drag
      this.drag = null
      if (!deltaDays) return
      const newEnd = new Date(new Date(task.planEnd).getTime() + deltaDays * 86400000)
        .toISOString().slice(0, 10)
      this.$http.post(`/project/tasks/${id}/adjust-date`, { planEnd: newEnd }).then(() => {
        this.$message.success(`完成日调整为 ${newEnd}，CPM 已重算`)
        this.load()
      }).catch(err => {
        showError(err)
        this.load()
      })
    },

    /* ---------- SPRINT-UID-R16：成员 / 门控 / ECO ---------- */
    roleDict(code) {
      return { OWNER: '项目经理', LEAD: '模块负责人', MEMBER: '执行成员' }[code] || code || '—'
    },
    showAddMember() {
      this.addMemberForm = { userId: '', roleCode: 'MEMBER' }
      this.addMemberDialog = true
    },
    doAddMember() {
      const userId = Number(this.addMemberForm.userId)
      if (!userId || userId <= 0) { this.$message.warning('请输入合法用户ID'); return }
      this.$http.post(`/project/${this.id}/members`, { userId, roleCode: this.addMemberForm.roleCode }).then(() => {
        this.$message.success('成员已添加')
        this.addMemberDialog = false
        this.load()
      }).catch(showError)
    },
    removeMember(row) {
      this.$confirm(`确定将成员 ${row.userId} 移出本项目？`, '移除成员').then(() => {
        this.$http.post(`/project/${this.id}/members/${row.userId}/remove`).then(() => {
          this.$message.success('已移除')
          this.load()
        }).catch(showError)
      }).catch(() => {})
    },
    gateStatusOf(row) {
      return row.status === 'DONE' ? 'PASSED' : 'PENDING'
    },
    /* ---------- SPRINT-UID-R18：ECO 影响面 + 任务依赖 ---------- */
    showEcoImpact(row) {
      this.ecoImpactTarget = row
      this.ecoImpactDialog = true
      this.$http.get(`/project/ecos/${row.id}/affected-tasks`, { params: { projectId: this.id } })
        .then(b => { this.ecoImpactTasks = b.data || [] })
        .catch(() => { this.ecoImpactTasks = [] })
    },
    addDependency() {
      if (!this.depForm.taskId || !this.depForm.preTaskId) {
        this.$message.warning('前后任务均必选')
        return
      }
      if (this.depForm.taskId === this.depForm.preTaskId) {
        this.$message.warning('任务不能依赖自身')
        return
      }
      this.$http.post(`/project/tasks/${this.depForm.taskId}/dependencies`, this.depForm).then(() => {
        this.$message.success('依赖已建立，CPM 将重算')
        this.depForm = { taskId: null, preTaskId: null, depType: 'FS', lagDays: 0 }
        this.load()
      }).catch(showError)
    },
    gateInfoOf(row) {
      const info = this.gateMap[row.id] || {}
      const total = info.totalChildren || 0
      const notDone = info.notDone == null ? total : info.notDone
      return { totalChildren: total, doneCount: Math.max(0, total - notDone), gateOpen: !!info.gateOpen }
    },
    passGate(row) {
      const info = this.gateInfoOf(row)
      const tip = info.gateOpen
        ? `阶段「${row.taskName}」子任务已全部完成，通过门控将放行下一阶段（FR-PRJ-38/39）`
        : `阶段「${row.taskName}」仍有未完成子任务，不可通过门控`
      this.$confirm(tip, '门控评审', {
        confirmButtonText: info.gateOpen ? '通过' : '知道了',
        cancelButtonText: '取消',
        type: info.gateOpen ? 'warning' : 'info'
      }).then(() => {
        if (!info.gateOpen) return
        this.$http.post(`/project/gate/${row.id}/pass`).then(() => {
          this.$message.success('门控已通过，下一阶段可开工')
          this.load()
        }).catch(showError)
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.pl-doc-head {
  height: 56px; display: flex; align-items: center; gap: 12px;
  background: var(--surface); border: 1px solid var(--line-2); border-radius: var(--r-3, 6px);
  padding: 0 20px; margin-bottom: 8px;
}
.pl-doc-no { font-family: var(--mono); font-size: 14px; color: var(--ink-2); }
.pl-doc-name { font-size: var(--fs-16, 16px); font-weight: 600; color: var(--ink); }
.spacer { flex: 1; }
.muted { font-size: 12px; color: var(--ink-3); }
.gantt-wrap { overflow-x: auto; padding: 4px 0; }
.gantt-row { display: flex; align-items: center; height: 34px; }
.gantt-label {
  width: 220px; flex: none; font-size: 12px; color: var(--ink-2);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; padding-right: 8px;
}
.gantt-track { position: relative; flex: 1; height: 26px; background: var(--surface-2, rgba(0,0,0,.03)); border-radius: 4px; }
.gantt-bar {
  position: absolute; top: 3px; height: 20px; border-radius: 4px;
  background: var(--primary, #409eff); opacity: .85; cursor: pointer;
  display: flex; align-items: center; overflow: hidden;
}
.gantt-bar.critical { background: var(--danger, #f56c6c); }
.gantt-bar.dragging { opacity: 1; box-shadow: 0 2px 8px rgba(0,0,0,.25); }
.gantt-bar-text { font-size: 11px; color: #fff; padding: 0 6px; white-space: nowrap; }
.cpm-yes { color: var(--danger, #f56c6c); font-weight: 600; font-size: 12px; }
.cpm-no { color: var(--ink-3); font-size: 12px; }
</style>
