<template>
  <!-- 表单分组区（UID-04 §四 PtFormSection）：组标题可折叠；缺失项计数 + 锚点定位（FR-CT-21 完整度） -->
  <div class="pt-section" :id="anchorId || undefined">
    <div class="pt-section-head" @click="toggle">
      <i :class="collapsedNow ? 'el-icon-arrow-right' : 'el-icon-arrow-down'"></i>
      <b>{{ title }}</b>
      <span v-if="errorCount > 0" class="pt-section-errors">{{ errorCount }} 项未通过</span>
      <span class="spacer"></span>
      <slot name="extra" />
    </div>
    <div v-show="!collapsedNow" class="pt-section-body">
      <slot />
    </div>
    <div v-if="$slots.footer && !collapsedNow" class="pt-section-footer"><slot name="footer" /></div>
  </div>
</template>

<script>
export default {
  name: 'PtFormSection',
  props: {
    title: { type: String, required: true },
    desc: { type: String, default: '' },
    anchorId: { type: String, default: '' },
    collapsed: { type: Boolean, default: false },
    errorCount: { type: Number, default: 0 }
  },
  data() { return { collapsedNow: this.collapsed } },
  methods: { toggle() { this.collapsedNow = !this.collapsedNow; this.$emit('toggle', this.collapsedNow) } }
}
</script>

<style scoped>
.pt-section { margin-bottom: 24px; }
.pt-section-head {
  display: flex; align-items: center; gap: 8px; height: 40px; cursor: pointer;
  font-size: var(--fs-16, 16px); color: var(--ink);
}
.pt-section-errors { font-size: 12px; color: var(--danger-ink); }
.pt-section-head .spacer { flex: 1; }
.pt-section-body { padding: 4px 0 0 24px; }
</style>
