<template>
  <!-- 接口监控（M15 FR-INT-06~08）：适配器健康检查 + 同步日志 -->
  <div class="pl-page">
    <el-tabs v-model="tab" type="card">
      <el-tab-pane label="适配器健康" name="adapters">
        <pt-table :columns="adapterCols" :data="adapters" :loading="loading"
                  :total="0" aria-label="适配器列表" empty-type="no-data" empty-label="适配器">
          <template slot="cell-healthStatus" slot-scope="{ row }">
            <pt-status-tag :value="row.healthStatus || 'UNKNOWN'"
                           :dict="'UP:正常,DOWN:异常,UNKNOWN:未检查'" />
          </template>
          <template slot="actions" slot-scope="{ row }">
            <el-button v-permission="'sys:config'" size="mini" type="text" @click.stop="check(row)">健康检查</el-button>
          </template>
        </pt-table>
      </el-tab-pane>
      <el-tab-pane label="同步日志" name="logs">
        <pt-table :columns="logCols" :data="logs" :loading="logLoading"
                  :total="0" aria-label="同步日志" empty-type="no-data" empty-label="日志">
          <template slot="cell-status" slot-scope="{ row }">
            <pt-status-tag :value="row.status" :dict="'PENDING:待推送,SUCCESS:成功,FAILED:失败'" />
          </template>
        </pt-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { PtTable, PtStatusTag } from '@/components/pt'
import { showError } from '@/api/request'
export default {
  name: 'SysMonitor',
  components: { PtTable, PtStatusTag },
  data() {
    return {
      tab: 'adapters',
      adapterCols: [
        { prop: 'adapterCode', label: '适配器编码', width: 140 },
        { prop: 'adapterName', label: '名称', minWidth: 160 },
        { prop: 'baseUrl', label: '地址', minWidth: 200 },
        { prop: 'enabled', label: '启用', width: 70 },
        { prop: 'healthStatus', label: '健康', width: 90 },
        { prop: 'lastCheckAt', label: '最近检查', width: 160 }
      ],
      adapters: [], loading: false,
      logCols: [
        { prop: 'adapterId', label: '适配器', width: 80 },
        { prop: 'direction', label: '方向', width: 80 },
        { prop: 'localId', label: '本地ID', width: 90 },
        { prop: 'remoteId', label: '远端ID', width: 120 },
        { prop: 'status', label: '状态', width: 100 },
        { prop: 'error_message', label: '错误', minWidth: 200 },
        { prop: 'pushedAt', label: '推送时间', width: 160 }
      ],
      logs: [], logLoading: false
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/int/adapters').then(body => {
        this.adapters = body.data || []
      }).catch(showError).finally(() => { this.loading = false })
      this.logLoading = true
      this.$http.get('/int/sync-logs').then(body => {
        this.logs = body.data || []
      }).catch(showError).finally(() => { this.logLoading = false })
    },
    check(row) {
      this.$http.post(`/int/adapters/${row.id}/health-check`).then(body => {
        this.$message.success('健康状态：' + (body.data && body.data.healthStatus))
        this.load()
      }).catch(showError)
    }
  }
}
</script>
