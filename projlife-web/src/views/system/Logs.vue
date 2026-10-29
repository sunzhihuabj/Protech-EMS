<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div class="pl-toolbar">
        <b>审计日志（只读，禁改禁删，03 册 §七）</b>
        <div class="spacer"></div>
        <el-select v-model="module" size="small" clearable placeholder="模块" style="width: 140px" @change="load">
          <el-option v-for="m in modules" :key="m" :value="m" :label="m" />
        </el-select>
        <el-button size="small" type="primary" icon="el-icon-search" @click="load">查询</el-button>
      </div>
      <el-table :data="records" v-loading="loading" size="small">
        <el-table-column type="expand">
          <template slot-scope="{ row }">
            <div class="json-box">
              <p><b>before：</b><code>{{ pretty(row.beforeJson) }}</code></p>
              <p><b>after：</b><code>{{ pretty(row.afterJson) }}</code></p>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column prop="userName" label="操作人" width="110" />
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="action" label="动作" width="160" />
        <el-table-column prop="targetType" label="对象类型" width="130" />
        <el-table-column prop="targetId" label="对象" width="140" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="traceId" label="traceId" min-width="200" show-overflow-tooltip />
      </el-table>
      <el-pagination background layout="total, prev, pager, next" :total="total"
                     :page-size="query.pageSize" :current-page.sync="query.pageNum" @current-change="load" />
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'LogsPage',
  data() {
    return {
      records: [],
      total: 0,
      loading: false,
      module: '',
      modules: ['org', 'system', 'contract', 'mdm', 'file', 'opportunity'],
      query: { pageNum: 1, pageSize: 20 }
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      const params = { ...this.query }
      if (this.module) params.module = this.module
      this.$http.get('/system/logs', { params }).then(body => {
        this.records = (body.data && body.data.records) || []
        this.total = (body.data && body.data.total) || 0
      }).catch(e => this.$showError(e)).finally(() => { this.loading = false })
    },
    pretty(json) {
      if (!json) return '—'
      try {
        return JSON.stringify(JSON.parse(json), null, 2)
      } catch (e) {
        return json
      }
    }
  }
}
</script>

<style scoped>
.json-box { padding: 8px 24px; font-size: 12px; }
.json-box code { white-space: pre-wrap; word-break: break-all; color: #556; }
</style>
