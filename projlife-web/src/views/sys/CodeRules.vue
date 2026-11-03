<template>
  <div class="pl-page">
    <el-card shadow="never" :body-style="{ padding: '16px' }">
      <div class="pl-toolbar">
        <span style="font-weight:600">编码规则（18 类，CodeTypes 单一入口）</span>
        <span class="spacer"></span>
        <span class="cr-note">取号统一走 CodeSequenceService（sys_code_sequence）；预览为真实取号，允许空档不回补</span>
      </div>
      <pt-table :columns="columns" :data="records" :loading="loading" :total="records.length"
                aria-label="编码规则" empty-type="no-data" empty-label="编码规则">
        <template slot="actions" slot-scope="{ row }">
          <el-button v-if="PREVIEWABLE.includes(row.type)" size="mini" type="text"
                     @click="preview(row.type)">预览下一号</el-button>
          <span v-else class="pl-empty-val">—</span>
        </template>
      </pt-table>
    </el-card>
  </div>
</template>
<script>
import { PtTable } from '@/components/pt'
import { showError } from '@/api/request'

/** preview 端点支持的真实取号类型（后端 CodeController.preview 的 8 类演示口径） */
const PREVIEWABLE = ['CONTRACT', 'PROJECT_ARCHIVE', 'OPPORTUNITY', 'PURCHASE_ORDER', 'DISPATCH', 'ECN', 'CUSTOMER', 'SUPPLIER']

export default {
  name: 'CodeRules',
  components: { PtTable },
  data() {
    return {
      PREVIEWABLE,
      columns: [
        { prop: 'type', label: '编码类型', width: 170 },
        { prop: 'name', label: '名称', width: 140 },
        { prop: 'format', label: '格式规则', minWidth: 220 },
        { prop: 'example', label: '示例', minWidth: 190 }
      ],
      records: [],
      loading: false
    }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      // SPRINT-UID-R15：修复死链——原 GET /org/codes 后端不存在（404），改调规则目录端点
      this.$http.get('/system/codes/rules').then(body => { this.records = body.data || [] })
        .catch(showError).finally(() => { this.loading = false })
    },
    preview(type) {
      this.$http.get('/system/codes/preview', { params: { type } }).then(body => {
        const d = body.data || {}
        this.$alert(`<div style="line-height:1.9">
            <div><b>作用域键：</b><span style="font-family:var(--mono,monospace)">${d.scopeKey || '—'}</span></div>
            <div><b>下一号：</b><span style="font-family:var(--mono,monospace);color:var(--pri-500,#1F7B8C);font-weight:600">${d.next || '—'}</span></div>
          </div>`, `${type} 取号预览`, { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' })
      }).catch(showError)
    }
  }
}
</script>
<style scoped>
.cr-note { font-size: 12px; color: var(--ink-3); }
</style>
