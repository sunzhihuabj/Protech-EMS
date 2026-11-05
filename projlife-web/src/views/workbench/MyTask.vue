<template>
  <!-- SPRINT-UID-R18：接真实 FR-PRJ-12 我的任务（原页面为项目列表伪装的任务骨架）+ FR-PRJ-25 部门负载 -->
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }" style="margin-bottom: 16px">
      <div slot="header" style="display: flex; justify-content: space-between; align-items: center">
        <b>我的任务（FR-PRJ-12）</b>
        <el-button size="mini" icon="el-icon-refresh" @click="load">刷新</el-button>
      </div>
      <pt-table :columns="columns" :data="tasks" :loading="loading" :total="total"
                :page.sync="page" @page-change="onPage"
                aria-label="我的任务" empty-type="no-data" empty-label="任务"
                empty-action-text="去项目管理" @empty-action="$router.push('/prj/project')">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status || 'TODO'"
                         :dict="'TODO:待处理,DOING:进行中,DONE:已完成,DELAYED:延期,CANCELLED:已取消'" />
        </template>
        <template slot="cell-progressPct" slot-scope="{ row }">
          <el-progress :percentage="Number(row.progressPct) || 0" :stroke-width="6" />
        </template>
        <template slot="cell-delayDays" slot-scope="{ row }">
          <span v-if="row.delayDays && row.delayDays > 0" class="delay">逾期 {{ row.delayDays }} 天</span>
          <span v-else class="muted">—</span>
        </template>
      </pt-table>
    </el-card>

    <!-- 部门负载（FR-PRJ-25：按 assignee 聚合工作日占比） -->
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <!-- FR-PRJ-25 -->
      <div slot="header"><b>部门负载</b></div>
      <div class="load-toolbar">
        <span class="muted">部门ID</span>
        <el-input-number v-model="loadQuery.deptId" :min="1" :controls="false" size="small" style="width: 90px" />
        <el-date-picker v-model="loadQuery.range" type="daterange" size="small" value-format="yyyy-MM-dd"
                        start-placeholder="开始" end-placeholder="结束" style="width: 240px" />
        <el-button size="small" type="primary" icon="el-icon-search" @click="loadDeptLoad">查询负载</el-button>
      </div>
      <el-empty v-if="!loadRows.length && !loadLoading" description="输入部门ID与日期范围查询负载" :image-size="50" />
      <el-table v-else v-loading="loadLoading" :data="loadRows" size="small">
        <el-table-column prop="userId" label="成员ID" width="90" />
        <el-table-column prop="taskCount" label="在办任务" width="100" align="right" />
        <el-table-column label="负载率" min-width="200">
          <template slot-scope="{ row }">
            <el-progress :percentage="Math.min(100, Number(row.loadPct) || 0)"
                         :status="row.loadPct > 120 ? 'exception' : (row.loadPct > 80 ? 'warning' : 'success')"
                         :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column width="80" align="right">
          <template slot-scope="{ row }">{{ row.loadPct }}%</template>
        </el-table-column>
      </el-table>
      <p class="muted" style="margin: 8px 0 0">负载率 = 成员在办任务与查询窗口重叠的工作日 ÷ 窗口工作日；>120% 红灯。</p>
    </el-card>
  </div>
</template>

<script>
import { PtTable, PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'
export default {
  name: 'MyTask',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      columns: [
        { prop: 'taskNo', label: '任务编号', width: 140 },
        { prop: 'taskName', label: '任务名称', minWidth: 200 },
        { prop: 'projectId', label: '项目ID', width: 90 },
        { prop: 'planStart', label: '计划开始', width: 108 },
        { prop: 'planEnd', label: '计划完成', width: 108 },
        { prop: 'progressPct', label: '进度', minWidth: 140 },
        { prop: 'delayDays', label: '逾期', width: 90 },
        { prop: 'status', label: '状态', width: 100 }
      ],
      tasks: [], total: 0, loading: false,
      page: { pageNum: 1, pageSize: 20 },
      loadQuery: { deptId: 1, range: [] },
      loadRows: [], loadLoading: false
    }
  },
  mounted() { this.load() },
  methods: {
    onPage(p) { this.page.pageNum = p.pageNum; this.page.pageSize = p.pageSize; this.load() },
    load() {
      this.loading = true
      this.$http.get('/project/my-tasks', { params: { pageNum: this.page.pageNum, pageSize: this.page.pageSize } })
        .then(body => {
          this.tasks = (body.data && body.data.records) || []
          this.total = (body.data && body.data.total) || 0
        }).catch(showError).finally(() => { this.loading = false })
    },
    loadDeptLoad() {
      if (!this.loadQuery.deptId) { this.$message.warning('部门ID必填'); return }
      const range = this.loadQuery.range || []
      if (!range[0] || !range[1]) { this.$message.warning('请选择日期范围'); return }
      this.loadLoading = true
      this.$http.get('/project/load', {
        params: { deptId: this.loadQuery.deptId, start: range[0], end: range[1] }
      }).then(body => {
        this.loadRows = body.data || []
      }).catch(showError).finally(() => { this.loadLoading = false })
    }
  }
}
</script>

<style scoped>
.muted { color: var(--ink-3); font-size: 12px; }
.delay { color: var(--danger, #f56c6c); font-size: 12px; }
.load-toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; }
</style>
