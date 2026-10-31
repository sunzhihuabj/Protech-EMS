<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div slot="header"><b>我的任务</b></div>
      <pt-table :columns="columns" :data="tasks" :loading="loading" :total="0"
                aria-label="我的任务" empty-type="no-data" empty-label="任务"
                empty-action-text="去项目管理" @empty-action="$router.push('/prj/project')">
        <template slot="cell-status" slot-scope="{ row }">
          <pt-status-tag :value="row.status || 'PENDING'"
                         :dict="'PENDING:待处理,IN_PROGRESS:进行中,DONE:已完成,BLOCKED:阻塞'" />
        </template>
      </pt-table>
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
        { prop: 'taskName', label: '任务名称', minWidth: 200 },
        { prop: 'projectName', label: '项目', width: 140 },
        { prop: 'assignee', label: '负责人', width: 90 },
        { prop: 'dueDate', label: '截止日', width: 112 },
        { prop: 'status', label: '状态', width: 110 }
      ],
      tasks: [], loading: false
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/prj/project', { params: { pageNum: 1, pageSize: 50 } }).then(body => {
        // 从项目列表提取当前用户任务（骨架：后续接 FR-PRJ-12 任务分派 API）
        const projects = body.data.records || []
        this.tasks = projects.map(p => ({
          taskName: p.projectName || p.stdName,
          projectName: p.stdName || '',
          assignee: this.$store.state.user ? this.$store.state.user.realName : '',
          dueDate: p.planEnd || '',
          status: p.status || 'PENDING'
        }))
      }).catch(showError).finally(() => { this.loading = false })
    }
  }
}
</script>
