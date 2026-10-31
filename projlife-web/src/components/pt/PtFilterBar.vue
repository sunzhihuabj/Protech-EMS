<template>
  <!-- 筛选区（UID-01 §7.1 / UID-02 §七 段1）：折叠 48（≤2 行 × 4 项，项宽 240）；
       已生效但被折叠的条件必须以 Tag 列入"已选："行；查询条件由页面写入 URL -->
  <div class="pt-filter">
    <div class="pt-filter-row" v-show="!collapsed || expanded">
      <el-form inline size="small" @submit.native.prevent>
        <el-form-item v-for="f in visibleFields" :key="f.prop" :label="f.label">
          <el-select v-if="f.type === 'select'" v-model="model[f.prop]" clearable filterable
                     :placeholder="f.label" style="width: 160px" @change="$emit('search', model)">
            <el-option v-for="o in f.options || []" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
          <el-date-picker v-else-if="f.type === 'date'" v-model="model[f.prop]" type="date"
                          value-format="yyyy-MM-dd" :placeholder="f.label" style="width: 150px"
                          @change="$emit('search', model)" />
          <el-input v-else v-model.trim="model[f.prop]" :placeholder="f.label" style="width: 200px"
                    @keyup.enter.native="$emit('search', model)" />
        </el-form-item>
      </el-form>
      <el-button v-if="hiddenCount > 0" type="text" size="mini" @click="expanded = !expanded">
        {{ expanded ? '收起' : '+' + hiddenCount + ' 项筛选' }}
      </el-button>
    </div>

    <!-- "已选："行：折叠隐藏但已生效的条件可见（防"隐形过滤"，UID-01 §7.1） -->
    <div v-if="collapsed && !expanded && activeHidden.length" class="pt-filter-chosen">
      <span class="pt-filter-chosen-label">已选：</span>
      <el-tag v-for="f in activeHidden" :key="f.prop" size="mini" closable
              @close="clearField(f)">{{ f.label }}: {{ model[f.prop] }}</el-tag>
    </div>

    <div class="pt-filter-actions">
      <el-button size="small" type="primary" icon="el-icon-search" @click="$emit('search', model)">查询</el-button>
      <el-button size="small" @click="reset">重置</el-button>
      <el-button v-if="collapsible" type="text" size="mini" @click="toggleExpand">
        {{ expanded ? '收起筛选' : '展开筛选' }}
      </el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'PtFilterBar',
  props: {
    fields: { type: Array, required: true },
    model: { type: Object, required: true },
    maxRows: { type: Number, default: 2 },
    collapsed: { type: Boolean, default: true },
    dirty: { type: Boolean, default: false }
  },
  data() {
    return { expanded: !this.collapsed }
  },
  computed: {
    collapsible() { return this.fields.length > this.perRow * this.maxRows },
    perRow() { return 4 },
    visibleFields() { return this.expanded ? this.fields : this.fields.slice(0, this.perRow * this.maxRows) },
    hiddenCount() { return Math.max(0, this.fields.length - this.perRow * this.maxRows) },
    activeHidden() {
      return this.fields.slice(this.perRow * this.maxRows)
        .filter(f => this.model[f.prop] !== undefined && this.model[f.prop] !== '' && this.model[f.prop] !== null)
    }
  },
  methods: {
    toggleExpand() {
      this.expanded = !this.expanded
      this.$emit('collapse-change', this.expanded)
    },
    clearField(f) {
      this.model[f.prop] = ''
      this.$emit('search', this.model)
    },
    reset() {
      this.fields.forEach(f => { this.model[f.prop] = '' })
      this.$emit('reset')
      this.$emit('search', this.model)
    }
  }
}
</script>

<style scoped>
.pt-filter { margin-bottom: 16px; }
.pt-filter-row { display: flex; align-items: flex-start; gap: 8px; }
.pt-filter-row >>> .el-form-item { margin-bottom: 8px; margin-right: 8px; }
.pt-filter-chosen { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.pt-filter-chosen-label { font-size: 12px; color: var(--ink-3); }
.pt-filter-actions { display: flex; gap: 8px; }
</style>
