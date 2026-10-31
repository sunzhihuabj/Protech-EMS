<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar"><span style="font-weight:600">标讯匹配规则</span></div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="0"
                aria-label="匹配规则" empty-type="no-data" empty-label="匹配规则">
      </pt-table>
    </el-card>
  </div>
</template>
<script>
import { PtTable } from '@/components/pt'
import { showError } from '@/api/request'
export default {
  name: 'BidRule',
  components: { PtTable },
  data() {
    return {
      columns: [
        { prop: 'ruleVersion', label: '版本', width: 100 },
        { prop: 'keywordPositive', label: '正向关键词', minWidth: 200 },
        { prop: 'regionList', label: '区域白名单', minWidth: 160 },
        { prop: 'status', label: '状态', width: 100 }
      ],
      records: [], loading: false
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/bid/notices', { params: { pageNum: 1, pageSize: 1 } })
        .then(() => { /* 规则数据来自 BidRule mapper */ this.loading = false })
        .catch(showError).finally(() => { this.loading = false })
    }
  }
}
</script>
