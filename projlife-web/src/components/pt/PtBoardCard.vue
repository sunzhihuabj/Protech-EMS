<template>
  <!-- 看板卡（五要素三行布局，FR-KB-04/05/06；缺项两级叠加呈现，UID-03 §2.2(c)） -->
  <div class="pt-board-card" :class="cardClass">
    <span v-if="missing.length" class="pt-board-card-todo" aria-label="待补全">待补全</span>

    <!-- ① 事项（第 1 行占满宽，≤2 行截断） -->
    <div v-if="card.title" class="pt-bc-title" :title="card.title">{{ card.title }}</div>
    <div v-else class="pt-bc-missing">未填：事项</div>

    <!-- ② 责任人（第 2 行左） + ③ 承诺日（第 2 行右 112） -->
    <div class="pt-bc-row2">
      <span v-if="card.owner" class="pt-bc-owner">负责：{{ card.owner }}</span>
      <span v-else class="pt-bc-missing pt-bc-owner">未指定</span>
      <span v-if="card.dueAt" class="pt-bc-due" :class="{ 'is-overdue': overdueDays > 0 }" aria-label="承诺日">
        {{ card.dueAt }}<template v-if="overdueDays > 0"> · 逾期 {{ overdueDays }} 天</template>
      </span>
      <span v-else class="pt-bc-missing pt-bc-due">未填：承诺日</span>
    </div>

    <!-- ④ 状态（第 3 行左，22 Tag + 灯 10×10） + ⑤ 卡点说明（RED/BLOCKED） -->
    <div class="pt-bc-row3">
      <pt-status-tag :value="card.status || 'TODO'" :dict="statusDict" />
      <pt-light v-if="card.light" :state="card.light" light-size="card" dense
                :label="lightLabel" />
      <span v-if="escalated" class="pt-bc-escalated" aria-label="已升级总板">已升级总板</span>
      <span v-if="card.blockReason" class="pt-bc-block" aria-label="卡点说明">卡点：{{ card.blockReason }}</span>
    </div>

    <!-- 升级第三通道：滞留文字 -->
    <div v-if="escalated" class="pt-bc-escalated-text">滞留 {{ staleDays }} 天 · 已自动升级总板</div>
  </div>
</template>

<script>
import PtStatusTag from './PtStatusTag.vue'
import PtLight from './PtLight.vue'

const STATUS_DICT = {
  TODO: '待办', DOING: '进行中', GREEN: '绿灯', YELLOW: '关注',
  RED: '红灯', BLOCKED: '阻塞', CLOSED: '已闭环'
}
const LIGHT_DICT = { GREEN: '正常', YELLOW: '关注', RED: '红灯', BLOCKED: '阻塞' }

export default {
  name: 'PtBoardCard',
  components: { PtStatusTag, PtLight },
  props: {
    /** 五要素：code/title/owner/dueAt/status + light/blockReason/escalated */
    card: { type: Object, required: true },
    dense: { type: Boolean, default: false }
  },
  computed: {
    missing() {
      const c = this.card
      return ['owner', 'dueAt'].filter(k => !c[k])
    },
    cardClass() {
      return {
        'is-dense': this.dense,
        'is-missing': this.missing.length > 0,
        'is-escalated': !!this.escalated
      }
    },
    statusDict() { return STATUS_DICT },
    lightLabel() { return this.card.light ? (LIGHT_DICT[this.card.light] || '') : '' },
    overdueDays() {
      if (!this.card.dueAt) return 0
      const due = new Date(String(this.card.dueAt).replace('T', ' ').slice(0, 10))
      return Math.max(0, Math.floor((Date.now() - due.getTime()) / 86400000))
    },
    escalated() { return !!this.card.escalated },
    staleDays() { return this.card.staleDays || this.overdueDays }
  }
}
</script>

<style scoped>
.pt-board-card {
  position: relative; width: 100%; padding: 16px; border-radius: var(--r-3, 6px);
  background: var(--surface); border: 1px solid var(--line-2); box-shadow: var(--elev-1);
  margin-bottom: 8px;
}
.pt-board-card.is-escalated { border-color: var(--danger-ink); }
.pt-board-card.is-missing { border: 1px dashed var(--danger); }
.pt-board-card-todo {
  position: absolute; top: 8px; right: 8px; height: 22px; padding: 0 8px;
  font-size: 12px; color: var(--ink-2); background: var(--surface-3); border-radius: var(--r-1, 2px);
}
.pt-bc-title {
  font-size: 14px; line-height: 22px; color: var(--ink); word-break: break-all;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.pt-bc-row2 { display: flex; justify-content: space-between; align-items: center; margin-top: 6px; }
.pt-bc-owner { font-size: 12px; color: var(--ink-3); }
.pt-bc-due { font-size: 12px; color: var(--ink-3); font-variant-numeric: tabular-nums; text-align: right; }
.pt-bc-due.is-overdue { color: var(--danger-ink); }
.pt-bc-row3 { display: flex; align-items: center; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.pt-bc-block { font-size: 12px; color: var(--ink-2); word-break: break-all; }
.pt-bc-escalated {
  height: 22px; padding: 0 8px; font-size: 12px; color: var(--ink-2);
  background: var(--surface-3); border-radius: var(--r-1, 2px); /* 中性 Tag，不用红色（UID-03 §2.2(d)） */
}
.pt-bc-escalated-text { margin-top: 6px; font-size: 12px; color: var(--danger-ink); font-variant-numeric: tabular-nums; }
/* 缺项位 = danger-bg 底 + 2px dashed 围框（内缩 2px）+ 12px 文案（UID-03 §2.2(c)） */
.pt-bc-missing {
  display: inline-block; padding: 1px 6px; margin: -1px -2px;
  background: var(--danger-bg); border: 2px dashed var(--danger); border-radius: var(--r-1, 2px);
  color: var(--danger-ink); font-size: 12px;
}
</style>
