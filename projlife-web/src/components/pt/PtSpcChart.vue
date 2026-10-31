<template>
  <!-- SPC X-bar 控制图（FR-QMS-09）：SVG 渲染，UCL/CL/LCL 三线 + 数据点 + 出控红标 -->
  <div class="pt-spc" :style="{ height: height + 'px' }">
    <svg :viewBox="'0 0 ' + width + ' ' + height" preserveAspectRatio="xMidYMid meet">
      <!-- 网格与控制线 -->
      <line v-for="l in guideLines" :key="'gl' + l.y" :x1="pad" :x2="width - pad"
            :y1="l.y" :y2="l.y" :stroke="l.color" stroke-dasharray="4,3" stroke-width="1" />
      <text v-for="l in guideLines" :key="'gt' + l.y" :x="width - pad + 4" :y="l.y + 4"
            font-size="10" :fill="l.color">{{ l.label }}</text>
      <!-- 数据连线 -->
      <polyline :points="polyPoints" fill="none" stroke="var(--pri-500)" stroke-width="1.5" />
      <!-- 数据点 -->
      <circle v-for="(p, i) in points" :key="i" :cx="p.x" :cy="p.y" r="4"
              :fill="p.ooc ? 'var(--danger)' : 'var(--pri-500)'" stroke="var(--surface)" stroke-width="1"
              :aria-label="'值 ' + p.val + (p.ooc ? ' 出控' : '')">
        <title v-if="p.ooc">出控！值 {{ p.val }} 超出控制限</title>
      </circle>
      <!-- X 轴标签 -->
      <text v-for="(p, i) in xLabels" :key="'x' + i" :x="p.x" :y="height - 8"
            font-size="10" fill="var(--ink-3)" text-anchor="middle" font-variant-numeric="tabular-nums">{{ p.text }}</text>
    </svg>
    <div class="pt-spc-legend">
      <span class="is-ooc">● 出控（{{ oocCount }}）</span>
      <span>● 受控</span>
      <span class="muted">UCL {{ ucl }} / CL {{ cl }} / LCL {{ lcl }}</span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'PtSpcChart',
  props: {
    data: { type: Array, required: true }, // [{valueX, sampleTime, ucl, lcl, cl, isOutOfControl}]
    height: { type: Number, default: 260 }
  },
  computed: {
    width() { return 680 },
    pad() { return 50 },
    values() { return this.data.map(d => Number(d.valueX || 0)) },
    ucl() { return this.data.length ? this.data[0].ucl : null },
    lcl() { return this.data.length ? this.data[0].lcl : null },
    cl() { return this.data.length ? this.data[0].cl : null },
    oocCount() { return this.data.filter(d => d.isOutOfControl).length },
    yRange() {
      const all = this.values.concat([this.ucl, this.lcl].filter(v => v !== null && v !== undefined).map(Number))
      if (!all.length) return { min: 0, max: 1 }
      const min = Math.min(...all) * 0.95
      const max = Math.max(...all) * 1.05
      return { min, max: max === min ? min + 1 : max }
    },
    yToPx() {
      const { min, max } = this.yRange
      const chartH = this.height - 40
      return v => this.pad + (1 - (v - min) / (max - min)) * chartH
    },
    guideLines() {
      const lines = []
      const chartW = this.width - this.pad * 2
      if (this.ucl !== null) lines.push({ y: this.yToPx(Number(this.ucl)), color: 'var(--danger)', label: 'UCL ' + this.ucl })
      if (this.cl !== null) lines.push({ y: this.yToPx(Number(this.cl)), color: 'var(--ink-3)', label: 'CL ' + this.cl })
      if (this.lcl !== null) lines.push({ y: this.yToPx(Number(this.lcl)), color: 'var(--danger)', label: 'LCL ' + this.lcl })
      return lines.map(l => ({ ...l, x2: this.pad + chartW }))
    },
    points() {
      const chartW = this.width - this.pad * 2
      const step = this.data.length > 1 ? chartW / (this.data.length - 1) : 0
      return this.data.map((d, i) => ({
        x: this.pad + i * step,
        y: this.yToPx(Number(d.valueX || 0)),
        val: d.valueX,
        ooc: !!d.isOutOfControl
      }))
    },
    polyPoints() { return this.points.map(p => p.x + ',' + p.y).join(' ') },
    xLabels() {
      return this.data.filter((_, i) => i % Math.max(1, Math.ceil(this.data.length / 8)) === 0)
        .map((d, j) => ({ x: this.pad + j * 10 * Math.max(1, Math.ceil(this.data.length / 8)), text: String(d.sampleTime || '').slice(5, 16) }))
    }
  }
}
</script>

<style scoped>
.pt-spc { width: 100%; }
.pt-spc svg { width: 100%; height: 100%; }
.pt-spc-legend { display: flex; gap: 16px; font-size: 12px; color: var(--ink-2); padding: 8px 0; }
.pt-spc-legend .is-ooc { color: var(--danger-ink); }
.muted { color: var(--ink-3); }
</style>
