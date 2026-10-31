<template>
  <view class="page">
    <view v-if="offline" class="offline-bar">离线中 · 已存 {{ queueCount }}/10 条</view>
    <form @submit="submit">
      <input v-model="form.date" type="date" placeholder="日期" />
      <textarea v-model="form.content" placeholder="施工日志内容（≥5 字）" :maxlength="500" />
      <button form-type="submit" :disabled="offline && queueCount >= 10">提交日志</button>
    </form>
  </view>
</template>
<script>
export default {
  data: () => ({
    offline: false, queueCount: 0,
    form: { date: '', content: '' }
  }),
  methods: {
    submit() {
      // NFR-13：离线队列（幂等键去重）；出库/验收必须联网（NFR-25）
    }
  }
}
</script>
<style scoped>
.page { padding: 12px; }
.offline-bar { background: #FFF3E0; padding: 8px 12px; border-radius: 4px; font-size: 12px; color: #E65100; margin-bottom: 12px; }
</style>
