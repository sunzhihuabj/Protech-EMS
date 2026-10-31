<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar"><span style="font-weight:600">设备台账</span></div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="0"
                aria-label="设备台账" empty-type="no-data" empty-label="设备">
      </pt-table>
    </el-card>
  </div>
</template>
<script>
import { PtTable } from '@/components/pt'
import { showError } from '@/api/request'
export default {
  name: 'EamAsset',
  components: { PtTable },
  data() {
    return {
      columns: [
        { prop: 'assetName', label: '设备名称', minWidth: 180 },
        { prop: 'assetCode', label: '设备编码', width: 140 },
        { prop: 'location', label: '位置', width: 140 },
        { prop: 'status', label: '状态', width: 100 }
      ],
      records: [], loading: false
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      this.$http.get('/eam/assets').then(body => {
        this.records = (body.data && body.data.records) || body.data || []
      }).catch(showError).finally(() => { this.loading = false })
    }
  }
}
</script>
