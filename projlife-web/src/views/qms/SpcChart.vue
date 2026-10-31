<template>
  <!-- SPC 控制图（FR-QMS-09）：X-bar 图 + 出控标记 + 特征过滤 -->
  <div class="pl-page">
    <el-card shadow="never">
      <div class="pl-toolbar">
        <el-input v-model="characteristic" placeholder="质量特征（如 外径）" size="small"
                  style="width: 200px" clearable @keyup.enter.native="load" />
        <el-button size="small" type="primary" icon="el-icon-search" @click="load">查询</el-button>
      </div>
      <pt-spc-chart v-if="spcData.length" :data="spcData" :height="280" />
      <div v-else class="pt-empty" style="text-align: center; padding: 48px 0">
        <i class="el-icon-data-line" style="font-size: 48px; color: var(--ink-4)"></i>
        <div class="pt-empty-title" style="font-size: 16px; font-weight: 600; color: var(--ink); margin: 8px 0 4px">暂无 SPC 数据</div>
        <div class="pt-empty-hint" style="font-size: 12px; color: var(--ink-3); margin-bottom: 12px">检验录入后自动生成（FR-QMS-09）</div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { PtSpcChart } from '@/components/pt'
import { showError } from '@/api/request'

export default {
  name: 'SpcChart',
  components: { PtSpcChart },
  data() {
    return { characteristic: '', spcData: [] }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.$http.get('/qms/spc', { params: { characteristic: this.characteristic || undefined } })
        .then(body => { this.spcData = body.data || [] })
        .catch(showError)
    }
  }
}
</script>
