<template>
  <!-- 状态 Tag 三语法（UID-01 §七.2）：流转态=描边 / 语义态=浅底 / 中性=灰底；高 22 --r-1 12px -->
  <span class="pt-status-tag" :class="cls" :style="style" @click="$emit('click', { value, label, code })">
    <span v-if="showCode" class="pt-status-code">{{ value }}</span>
    <span>{{ label }}</span>
  </span>
</template>

<script>
/** 语义色映射（浅底 + -ink 字，不随主题只随明暗） */
const SEMANTIC = {
  RED: { bg: 'var(--danger-bg)', ink: 'var(--danger-ink)', bd: 'var(--danger-ink)' },
  YELLOW: { bg: 'var(--warn-bg)', ink: 'var(--warn-ink)', bd: 'var(--warn-ink)' },
  GREEN: { bg: 'var(--ok-bg)', ink: 'var(--ok-ink)', bd: 'var(--ok-ink)' },
  BLOCKED: { bg: 'transparent', ink: 'var(--ink-2)', bd: 'var(--flat)' },
  OK: { bg: 'var(--ok-bg)', ink: 'var(--ok-ink)', bd: 'var(--ok-ink)' }
}

export default {
  name: 'PtStatusTag',
  props: {
    value: { type: String, required: true },
    dict: { type: [String, Object], required: true }, // 'RED:红灯,YELLOW:关注,...' 或 {RED:'红灯'}
    size: { type: String, default: 'small' },
    showCode: { type: Boolean, default: false }
  },
  computed: {
    map() {
      if (typeof this.dict === 'object') return this.dict
      const m = {}
      this.dict.split(',').forEach(pair => {
        const [k, v] = pair.split(':')
        if (k && v) m[k.trim()] = v.trim()
      })
      return m
    },
    label() { return this.map[this.value] || this.value },
    semantic() { return SEMANTIC[this.value] || null },
    cls() { return this.semantic ? 'pt-status-tag--semantic' : 'pt-status-tag--flow' },
    style() {
      if (!this.semantic) {
        // 流转态：描边（--tag-pr-bd + --tag-pr-ink + 透明底，任何行底下不消失）
        return { borderColor: 'var(--tag-pr-bd)', color: 'var(--tag-pr-ink)', background: 'transparent' }
      }
      return { background: this.semantic.bg, color: this.semantic.ink, borderColor: this.semantic.bd }
    }
  }
}
</script>

<style scoped>
.pt-status-tag {
  display: inline-flex; align-items: center; gap: 4px;
  height: 22px; padding: 0 8px; border-radius: var(--r-1, 2px);
  font-size: 12px; line-height: 22px; border: 1px solid transparent;
  cursor: default; white-space: nowrap;
}
.pt-status-tag--semantic { cursor: default; }
.pt-status-code { font-family: var(--mono); opacity: .8; }
</style>
