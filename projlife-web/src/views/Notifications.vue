<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div slot="header" class="pl-toolbar">
        <b>消息中心</b>
        <div class="spacer"></div>
        <el-radio-group v-model="unreadOnly" size="mini" @change="load">
          <el-radio-button :label="false">全部</el-radio-button>
          <el-radio-button :label="true">未读</el-radio-button>
        </el-radio-group>
      </div>
      <div v-if="records.length === 0" class="empty">暂无消息</div>
      <el-table v-else :data="records" size="small">
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="状态" width="90">
          <template slot-scope="{ row }">
            <el-tag size="mini" :type="row.isRead ? 'info' : 'danger'">{{ row.isRead ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template slot-scope="{ row }">
            <el-button v-if="!row.isRead" size="mini" type="text" @click="markRead(row)">标记已读</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination background layout="total, prev, pager, next" :total="total"
                     :page-size="query.pageSize" :current-page.sync="query.pageNum" @current-change="load" />
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'Notifications',
  data() {
    return {
      records: [],
      total: 0,
      unreadOnly: false,
      query: { pageNum: 1, pageSize: 20 }
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    load() {
      this.$http.get('/notifications', { params: { ...this.query, unreadOnly: this.unreadOnly } }).then(body => {
        this.records = (body.data && body.data.records) || []
        this.total = (body.data && body.data.total) || 0
      }).catch(e => this.$showError(e))
    },
    markRead(row) {
      this.$http.put(`/notifications/${row.id}/read`).then(() => {
        this.load()
        this.$store.dispatch('fetchMe')
      }).catch(e => this.$showError(e))
    }
  }
}
</script>

<style scoped>
.empty { color: var(--ink-4); padding: 24px 0; text-align: center; }
</style>
