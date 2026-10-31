<template>
  <view class="page">
    <image v-if="watermarkUrl" :src="watermarkUrl" mode="widthFix" class="doc-img" />
    <view v-else class="empty"><text>加载水印件…</text></view>
    <!-- BR-07：只读水印件，禁止下载原图/另存为 -->
  </view>
</template>
<script>
export default {
  data: () => ({ watermarkUrl: '' }),
  onLoad(query) { /* GET /fs/files/{id}/preview → 水印件 URL */ }
}
</script>
<style scoped>
.page { padding: 12px; }
.doc-img { width: 100%; border-radius: 8px; }
.empty { text-align: center; padding: 48px 0; color: #999; }
</style>
