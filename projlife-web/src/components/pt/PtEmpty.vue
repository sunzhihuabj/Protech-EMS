<template>
  <!-- SPRINT-UI-2 空态插画（FR-SYS-24 三型）：工程蓝图风 SVG，描边走令牌，禁自创 hex -->
  <div class="pt-empty-ill">
    <!-- no-data：待填充的图纸（图纸框 + 虚线轮廓 + 基准角标） -->
    <svg v-if="type === 'no-data'" viewBox="0 0 120 84" width="120" height="84" aria-hidden="true">
      <g class="pe-grid">
        <line v-for="i in 5" :key="'v' + i" :x1="i * 20" y1="6" :x2="i * 20" y2="78" />
        <line v-for="i in 3" :key="'h' + i" x1="8" :y1="i * 20" x2="112" :y2="i * 20" />
      </g>
      <rect x="26" y="14" width="68" height="52" rx="3" class="pe-sheet" />
      <path d="M34 56 L46 40 L56 48 L70 30 L86 52" class="pe-stroke" />
      <line x1="34" y1="24" x2="62" y2="24" class="pe-dash" />
      <circle cx="86" cy="22" r="4" class="pe-dot" />
      <path d="M26 14 h-6 v6 M94 66 h6 v-6" class="pe-corner" />
    </svg>
    <!-- no-result：蓝图网格上的放大镜（检索无果） -->
    <svg v-else-if="type === 'no-result'" viewBox="0 0 120 84" width="120" height="84" aria-hidden="true">
      <g class="pe-grid">
        <line v-for="i in 5" :key="'v' + i" :x1="i * 20" y1="6" :x2="i * 20" y2="78" />
        <line v-for="i in 3" :key="'h' + i" x1="8" :y1="i * 20" x2="112" :y2="i * 20" />
      </g>
      <circle cx="56" cy="38" r="18" class="pe-stroke" />
      <line x1="70" y1="52" x2="88" y2="70" class="pe-stroke" />
      <path d="M48 38 h16 M56 30 v16" class="pe-dash" />
      <circle cx="94" cy="18" r="3" class="pe-dot" />
      <circle cx="18" cy="66" r="3" class="pe-dot" />
    </svg>
    <!-- no-scope：数据范围盾（权限边界） -->
    <svg v-else viewBox="0 0 120 84" width="120" height="84" aria-hidden="true">
      <g class="pe-grid">
        <line v-for="i in 5" :key="'v' + i" :x1="i * 20" y1="6" :x2="i * 20" y2="78" />
        <line v-for="i in 3" :key="'h' + i" x1="8" :y1="i * 20" x2="112" :y2="i * 20" />
      </g>
      <path d="M60 12 L88 22 V44 C88 60 76 68 60 74 C44 68 32 60 32 44 V22 Z" class="pe-sheet" />
      <path d="M48 42 L57 51 L74 32" class="pe-stroke" />
      <circle cx="92" cy="16" r="3" class="pe-dot" />
      <circle cx="22" cy="60" r="3" class="pe-dot" />
    </svg>
    <div class="pt-empty-ill-title">{{ title }}</div>
    <div class="pt-empty-ill-hint">{{ hint }}</div>
    <div class="pt-empty-ill-actions">
      <slot />
    </div>
  </div>
</template>

<script>
export default {
  name: 'PtEmpty',
  props: {
    type: { type: String, default: 'no-data' }, // no-data | no-result | no-scope
    title: { type: String, required: true },
    hint: { type: String, default: '' }
  }
}
</script>

<style scoped>
.pt-empty-ill { padding: 28px 0 20px; display: flex; flex-direction: column; align-items: center; }
.pe-grid line { stroke: var(--line); stroke-width: 1; opacity: .6; }
.pe-sheet { fill: var(--pri-soft); stroke: var(--ink-4); stroke-width: 1.5; }
.pe-stroke { fill: none; stroke: var(--pri-400, var(--ink-3)); stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.pe-dash { fill: none; stroke: var(--ink-4); stroke-width: 1.5; stroke-dasharray: 4 3; }
.pe-dot { fill: var(--pri-300, var(--ink-4)); }
.pe-corner { fill: none; stroke: var(--ink-4); stroke-width: 1.5; }
.pt-empty-ill-title { font-size: 16px; font-weight: 600; color: var(--ink); margin: 12px 0 4px; }
.pt-empty-ill-hint { font-size: 12px; color: var(--ink-3); }
.pt-empty-ill-actions { margin-top: 14px; display: flex; gap: 8px; }
@media (prefers-reduced-motion: reduce) {
  .pt-empty-ill * { animation: none !important; transition: none !important; }
}
</style>
