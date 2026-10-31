<template>
  <!-- 指标卡（UID-01 §七.3）：高 96、三行固定布局；分母 0 显示"—"禁 0%；整卡可点下钻 -->
  <div class="pt-metric" :class="{ 'is-clickable': !!$listeners.click }" @click="$emit('click', { label, actual })">
    <div class="pt-metric-name">
      {{ label }}
      <span v-if="manual" class="pt-metric-badge">手工</span>
    </div>
    <div class="pt-metric-value-row">
      <span v-if="actual === null" class="pt-metric-nodata" aria-label="无数据">—</span>
      <span v-else class="pt-metric-value">{{ displayActual }}</span>
      <pt-light v-if="state" :state="state" light-size="metric" dense />
    </div>
    <div class="pt-metric-target-row">
      <span class="pt-metric-target">目标 {{ target === null ? '—' : displayTarget }}{{ unit }}</span>
      <span v-if="trend === 1" class="pt-metric-trend is-up">▲</span>
      <span v-else-if="trend === -1" class="pt-metric-trend is-down">▼</span>
    </div>
    <i v-if="$listeners.click" class="el-icon-arrow-right pt-metric-arrow" aria-hidden="true"></i>
  </div>
</template>

<script>
import PtLight from './PtLight.vue'

export default {
  name: 'PtMetric',
  components: { PtLight },
  props: {
    label: { type: String, required: true },
    target: { type: Number, default: null },
    actual: { type: Number, default: null },
    unit: { type: String, default: '' },
    state: { type: String, default: '' },
    precision: { type: Number, default: 0 },
    trend: { type: Number, default: 0 },
    manual: { type: Boolean, default: false }
  },
  computed: {
    displayActual() {
      if (this.actual === null) return null
      return Number(this.actual).toFixed(this.precision)
    },
    displayTarget() {
      if (this.target === null) return '—'
      return Number(this.target).toFixed(this.precision)
    }
  }
}
</script>

<style scoped>
.pt-metric {
  position: relative; height: 96px; padding: 12px 16px; border-radius: var(--r-3, 6px);
  background: var(--surface); border: 1px solid var(--line-2); box-shadow: var(--elev-1);
  min-width: 0;
}
.pt-metric.is-clickable { cursor: pointer; }
.pt-metric.is-clickable:hover { background: var(--row-hover); }
.pt-metric-name { font-size: 12px; color: var(--ink-3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pt-metric-badge {
  margin-left: 4px; padding: 0 4px; border: 1px solid var(--line-2); border-radius: var(--r-1, 2px);
  font-size: 11px; color: var(--ink-2);
}
.pt-metric-value-row { display: flex; align-items: center; gap: 6px; margin: 4px 0 2px; }
.pt-metric-value { font-size: 28px; font-weight: 700; color: var(--ink); font-variant-numeric: tabular-nums; }
.pt-metric-nodata { font-size: 20px; color: var(--ink-3); }
.pt-metric-target-row { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--ink-3); font-variant-numeric: tabular-nums; }
.pt-metric-trend.is-up { color: var(--up); }
.pt-metric-trend.is-down { color: var(--down); }
.pt-metric-arrow { position: absolute; right: 10px; bottom: 8px; font-size: 16px; color: var(--ink-4); }
</style>
