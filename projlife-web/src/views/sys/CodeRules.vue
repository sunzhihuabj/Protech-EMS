<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar"><span style="font-weight:600">编码规则</span></div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="0"
                aria-label="编码规则" empty-type="no-data" empty-label="编码规则">
      </pt-table>
    </el-card>
  </div>
</template>
<script>
import { PtTable } from '@/components/pt'
import { showError } from '@/api/request'
export default {
  name: 'CodeRules',
  components: { PtTable },
  data() {
    return {
      columns: [
        { prop: 'code_key', label: '编码键', width: 180 },
        { prop: 'prefix', label: '前缀', width: 160 },
        { prop: 'current_value', label: '当前值', width: 100, align: 'right' },
        { prop: 'updated_at', label: '更新时间', minWidth: 160 }
      ],
      records: [], loading: false
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/org/codes').then(body => { this.records = body.data || [] })
        .catch(showError).finally(() => { this.loading = false })
    }
  }
}
</script>
