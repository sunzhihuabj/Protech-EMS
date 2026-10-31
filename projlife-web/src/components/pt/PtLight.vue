<template>
  <!-- 灯（E-52 四编码）：形状第一编码 + 前缀字 + aria（可访问名含状态词，UID-05 验收 13） -->
  <span class="pt-light" :class="cls" :style="boxStyle" role="img"
        :aria-label="labelText || stateLabel">
    <span class="pt-light-shape" aria-hidden="true">{{ shape }}</span>
    <span v-if="showLabelText" class="pt-light-label">{{ labelText || stateLabel }}</span>
  </span>
</template>

<script>
/** 灯尺寸阶梯（UID-01 §七.2）：table 8×8（唯一例外，同列有中文状态字）/ card 10×10 / metric 12×12 */
const SIZES = { table: 8, card: 10, metric: 12 }
const STATE_META = {
  GREEN: { shape: '●', label: '正常', color: 'var(--ok-ink)', bg: 'var(--ok)' },
  YELLOW: { shape: '▲', label: '关注', color: 'var(--warn-ink)', bg: 'var(--warn)' },
  RED: { shape: '■', label: '红灯', color: 'var(--danger-ink)', bg: 'var(--danger)' },
  BLOCKED: { shape: '◯', label: '阻塞', color: 'var(--ink-2)', bg: 'transparent' }
}

export default {
  name: 'PtLight',
  props: {
    state: { type: String, required: true, validator: v => Object.keys(STATE_META).indexOf(v) !== -1 },
    label: { type: String, default: '' },
    lightSize: { type: String, default: 'card' }, // 'table' | 'card' | 'metric'（不提供任意像素）
    dense: { type: Boolean, default: false }
  },
  computed: {
    meta() { return STATE_META[this.state] || STATE_META.GREEN },
    shape() { return this.meta.shape },
    stateLabel() { return this.meta.label },
    labelText() { return this.label },
    showLabelText() { return !this.dense && this.lightSize !== 'table' },
    boxStyle() {
      const px = SIZES[this.lightSize] || 10
      return {
        width: px + 'px',
        height: px + 'px',
        background: this.state === 'BLOCKED' ? 'transparent' : this.meta.bg,
        border: this.state === 'BLOCKED' ? '2px solid var(--flat)' : '1px solid ' + this.meta.color
      }
    },
    cls() { return 'pt-light--' + this.state.toLowerCase() }
  }
}
</script>

<style scoped>
.pt-light { display: inline-flex; align-items: center; gap: 4px; }
.pt-light-shape { display: inline-flex; align-items: center; justify-content: center; line-height: 1; }
.pt-light-label { font-size: 12px; color: var(--ink-2); }
</style>
