<template>
  <view class="page">
    <view class="hint"><text>销项扫码（NFR-25 必须联网）</text></view>
    <button :disabled="offline" @click="scan">扫码销项</button>
  </view>
</template>
<script>
export default {
  data: () => ({ offline: false }),
  methods: { scan() { /* uni.scanCode → POST /eng/writeoff/scan */ } }
}
</script>
<style scoped>
.page { padding: 12px; }
.hint { text-align: center; padding: 24px 0; color: #999; }
</style>
