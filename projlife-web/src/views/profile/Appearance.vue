<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div slot="header">
        <b>外观设置</b>
        <span class="muted">12 套预设主题（03 册 §6A）：个人偏好覆盖系统默认（当前系统默认：{{ defaultTheme }}）；
        切换即时生效，无需保存按钮；偏好持久化到服务端，多端一致；不允许自定义色值</span>
      </div>

      <div class="pl-theme-grid">
        <div v-for="t in themes" :key="t.key" class="pl-theme-card"
             :class="{ 'is-active': t.key === effectiveTheme }"
             :style="t.key === effectiveTheme ? { borderColor: t.p500 } : {}" @click="choose(t)">
          <div class="pl-theme-swatch" :style="{ background: `linear-gradient(135deg, ${t.p500}, ${t.p800})` }"></div>
          <div>
            <span class="pl-theme-name">{{ t.name }}</span>
            <span class="pl-theme-grade">{{ t.grade }} · {{ t.key }}</span>
          </div>
        </div>
      </div>

      <div style="margin-top: 16px">
        <el-button size="small" @click="followDefault">清除个人偏好（跟随系统默认 {{ defaultTheme }}）</el-button>
      </div>
      <p class="muted" style="margin-top: 12px">
        档位说明：推荐/可用/实验/反例为视觉风险标注，全部 12 套均可选用（P1 全可选，无禁用项）。
      </p>
    </el-card>
  </div>
</template>

<script>
import manifest from '@/theme/themes.json'
import { applyTheme } from '@/theme/theme'
import { showError } from '@/api/request'

export default {
  name: 'Appearance',
  data() {
    return { themes: manifest.themes }
  },
  computed: {
    defaultTheme() {
      return this.$store.state.defaultTheme || 't1'
    },
    effectiveTheme() {
      return (this.$store.state.user && this.$store.state.user.themeCode) || this.defaultTheme
    }
  },
  methods: {
    choose(theme) {
      // 即时生效（无「保存」按钮，phase2 §3 口径）+ 服务端持久化
      applyTheme(theme.key, true)
      this.$store.dispatch('saveTheme', theme.key).catch(showError)
    },
    followDefault() {
      this.$store.dispatch('resetTheme').then(() => {
        this.$message.success('已跟随系统默认')
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.muted { color: #8a97a3; font-size: 12px; margin-left: 12px; font-weight: 400; }
</style>
