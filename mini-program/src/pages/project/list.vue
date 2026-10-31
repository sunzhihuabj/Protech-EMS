<template>
  <view class="page">
    <view v-for="p in projects" :key="p.id" class="card" @click="goDetail(p)">
      <text class="card-name">{{ p.stdName }}</text>
      <text class="card-meta">{{ p.farmName }} · {{ p.setQty }} 台 · {{ p.status }}</text>
    </view>
    <view v-if="!projects.length" class="empty"><text>暂无授权项目</text></view>
  </view>
</template>
<script>
export default {
  data: () => ({ projects: [] }),
  onShow() { /* GET /thd/my-projects（授权过滤，R17 只见本项目） */ },
  methods: { goDetail(p) { uni.navigateTo({ url: '/pages/log/edit?projectId=' + p.id }) } }
}
</script>
<style scoped>
.page { padding: 12px; }
.card { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 12px; }
.card-name { display: block; font-size: 15px; font-weight: 600; }
.card-meta { display: block; font-size: 12px; color: #999; margin-top: 4px; }
.empty { text-align: center; padding: 48px 0; color: #999; }
</style>
