<template>
  <!-- P4 台账列表四段范式的表格段（UID-02 §七 / UID-04 §四）：
       表头 40 / 行高 --row-h 三档 / 禁斑马纹 / 固定首末列 / 空态三型+主行动 / 脱敏 *** -->
  <div class="pt-table">
    <!-- 2 工具栏：左操作（slot）/ 右视图切换；批量条浮于表头之上替换表头 -->
    <div class="pt-table-toolbar">
      <div v-if="selectedCount > 0" class="pt-table-batch">
        已选 {{ selectedCount }} 条（跨 {{ batchPages }} 页）
        <slot name="batch" :rows="selectedRows" :count="selectedCount" />
        <el-button type="text" size="mini" @click="clearSelection">取消选择</el-button>
      </div>
      <div v-else class="pt-table-toolbar-main"><slot name="toolbar" /></div>
    </div>

    <!-- 3 表格 -->
    <el-table ref="table" :data="data" v-loading="loading" :row-key="rowKey"
              :height="height" size="small" highlight-current-row
              :empty-text="' '" @sort-change="onSort" @selection-change="onSelection"
              @row-click="row => $emit('row-click', row)"
              :aria-label="ariaLabel" :aria-busy="String(loading)">
      <el-table-column v-if="selectable" type="selection" width="40" />
      <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop"
                       :label="col.label + (isMasked(col) ? '（无查看权限）' : '')"
                       :width="col.width" :min-width="col.minWidth || (col.type === 'money' ? 120 : undefined)"
                       :align="col.align || alignOf(col)" :sortable="col.sortable || false"
                       :fixed="col.fixed" :show-overflow-tooltip="col.type !== 'action'">
        <template slot-scope="{ row, $index }">
          <slot v-if="$scopedSlots['cell-' + col.prop]" :name="'cell-' + col.prop"
                :row="row" :value="row[col.prop]" :$index="$index" />
          <template v-else-if="isMasked(col)">
            <span class="pl-masked-val" aria-label="无查看权限">***</span>
          </template>
          <template v-else>{{ render(row, col) }}</template>
        </template>
      </el-table-column>
      <el-table-column v-if="$scopedSlots.actions" label="操作" :width="actionWidth" fixed="right">
        <template slot-scope="{ row }"><slot name="actions" :row="row" /></template>
      </el-table-column>
      <!-- 空态（FR-SYS-24 三型：48 图标 + 主文案 16 + 说明 12 + 主行动 32；禁"暂无数据"裸文案） -->
      <template slot="empty">
        <div class="pt-empty">
          <i class="el-icon-document" aria-hidden="true"></i>
          <div class="pt-empty-title">{{ emptyTitle }}</div>
          <div class="pt-empty-hint">{{ emptyHint }}</div>
          <el-button v-if="emptyActionText" size="small" type="primary"
                     @click="$emit('empty-action')">{{ emptyActionText }}</el-button>
          <el-button v-if="emptyType === 'no-result'" size="small" @click="$emit('empty-action')">清除筛选</el-button>
        </div>
      </template>
    </el-table>

    <!-- 4 分页 56：左总数/每页条数，右页码器 -->
    <div class="pt-table-pager">
      <span class="pt-total">共 {{ totalLabel }} 条</span>
      <el-select :value="page.pageSize" size="mini" style="width: 90px"
                 @input="v => change({ pageNum: 1, pageSize: v })">
        <el-option v-for="s in [20, 50, 100, 200]" :key="s" :value="s" :label="s + ' 条/页'" />
      </el-select>
      <div class="spacer"></div>
      <el-pagination background layout="prev, pager, next, jumper" :total="total"
                     :page-size="page.pageSize" :current-page.sync="currentPage"
                     @current-change="p => change({ pageNum: p, pageSize: page.pageSize })" />
    </div>
  </div>
</template>

<script>
/** 金额/数量/日期/编码一律 tabular（UID-01 §二 强制清单）；列宽策略 编码120|名称flex200|日期112|金额120|状态96|操作88 */
export default {
  name: 'PtTable',
  props: {
    columns: { type: Array, required: true },
    data: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    total: { type: Number, default: 0 },
    page: { type: Object, default: () => ({ pageNum: 1, pageSize: 20 }) },
    rowKey: { type: String, default: 'id' },
    selectable: { type: Boolean, default: false },
    virtual: { type: Boolean, default: false },
    height: { type: [String, Number], default: undefined },
    ariaLabel: { type: String, default: '列表' },
    maskedCols: { type: Array, default: () => [] },
    emptyType: { type: String, default: 'no-data' }, // no-data | no-result | no-scope
    emptyLabel: { type: String, default: '数据' },
    emptyActionText: { type: String, default: '' },
    emptyHint: { type: String, default: '' }
  },
  data() {
    return { selectedRows: [], currentPage: this.page.pageNum }
  },
  computed: {
    selectedCount() { return this.selectedRows.length },
    batchPages() { return Math.max(1, Math.ceil(this.selectedCount / this.page.pageSize)) },
    actionWidth() {
      const n = (this.$scopedSlots.actions && this.columns.length >= 0) ? 1 : 0
      return Math.max(88, n * 88)
    },
    totalLabel() { return this.total > 50000 ? '约 ' + this.total : this.total },
    emptyTitle() {
      return { 'no-data': '还没有' + this.emptyLabel, 'no-result': '没有符合条件的' + this.emptyLabel, 'no-scope': '你的数据范围下暂无数据' }[this.emptyType]
    }
  },
  methods: {
    isMasked(col) { return col.masked || this.maskedCols.indexOf(col.prop) !== -1 },
    alignOf(col) { return ['money', 'num'].indexOf(col.type) !== -1 ? 'right' : undefined },
    render(row, col) {
      const v = row[col.prop]
      if (v === null || v === undefined || v === '') {
        return '—' // 无值（--ink-3，aria 由 formatter 出口统一，UID-00 A-06）
      }
      if (col.type === 'money') return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
      return v
    },
    change(p) { this.currentPage = p.pageNum; this.$emit('page-change', p) },
    onSort({ prop, order }) { this.$emit('sort-change', { prop, order }) },
    onSelection(rows) { this.selectedRows = rows; this.$emit('selection-change', rows) },
    clearSelection() { this.$refs.table.clearSelection() }
  }
}
</script>

<style scoped>
.pt-table { display: flex; flex-direction: column; }
.pt-table-toolbar { height: 48px; display: flex; align-items: center; margin-bottom: 0; }
.pt-table-toolbar-main { display: flex; gap: 8px; align-items: center; width: 100%; }
.pt-table-toolbar-main >>> .spacer, .pt-table-batch { display: flex; align-items: center; gap: 8px; }
.pt-table-toolbar-main >>> .spacer { flex: 1; }
.pt-table-batch {
  width: 100%; background: var(--pri-soft); border-radius: var(--r-2, 4px);
  padding: 0 12px; font-size: 13px; color: var(--ink);
}
.pt-table-batch >>> .el-button { margin-left: 8px; }
.pt-table >>> .el-table th { font-weight: 600; }
.pt-table-pager { height: 56px; display: flex; align-items: center; gap: 12px; }
.pt-table-pager .spacer { flex: 1; }
.pt-total { font-size: 12px; color: var(--ink-2); font-variant-numeric: tabular-nums; }
.pt-empty { padding: 24px 0; }
.pt-empty >>> .el-icon-document { font-size: 48px; color: var(--ink-4); }
.pt-empty-title { font-size: 16px; font-weight: 600; color: var(--ink); margin: 8px 0 4px; }
.pt-empty-hint { font-size: 12px; color: var(--ink-3); margin-bottom: 12px; }
</style>
