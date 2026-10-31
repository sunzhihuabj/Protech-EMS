<template>
  <!-- 422 阻断条（三段式强制，UID-01 §7.4 / UID-03 §2.4(d) / V4-01 §6.2）：
       blockCode 徽标（12 mono）+ 一句话原因 + 「去处理 →」直达链接；不自动消失 -->
  <div class="pl-block-alert" role="alert" aria-live="assertive">
    <i class="el-icon-warning-outline" aria-hidden="true"></i>
    <span class="pl-block-code">{{ blockCode }}</span>
    <span class="pl-block-reason">{{ reason }}</span>
    <span v-if="detail" class="pl-block-detail">{{ detail }}</span>
    <span class="pl-block-link">
      <el-button v-if="link" type="text" size="mini" @click="go">{{ linkText }} →</el-button>
      <el-button v-else-if="dismissible" type="text" size="mini" @click="$emit('dismiss')">关闭</el-button>
    </span>
  </div>
</template>

<script>
export default {
  name: 'PtBlockAlert',
  props: {
    blockCode: { type: String, required: true },
    reason: { type: String, required: true },
    link: { type: String, default: '' },        // UID-05 已登记的真实路由，带 ?from= 回跳
    linkText: { type: String, default: '去处理' },
    detail: { type: String, default: '' },      // 服务端 suggestion
    dismissible: { type: Boolean, default: false },
    keepData: { type: Boolean, default: true }
  },
  methods: {
    go() {
      if (this.keepData) {
        // 跳转前快照：sessionStorage（键含路由），回来 mounted 回填（L-1 不丢数据）
        try {
          sessionStorage.setItem('pl:block:return:' + this.link, JSON.stringify({
            from: this.$route.fullPath, at: Date.now()
          }))
        } catch (e) { /* 忽略 */ }
      }
      this.$emit('action', { blockCode: this.blockCode })
      this.$router.push(this.link)
    }
  }
}
</script>

<style scoped>
.pl-block-detail { font-size: 12px; color: var(--danger-ink); }
</style>
