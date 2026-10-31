<template>
  <div class="pl-page">
    <el-card shadow="never">
      <div slot="header">
        <b>外观设置</b>
        <span class="muted">12 套主题 × 亮/暗 = 24 组（UID-00 D-UI-04）；切换即时生效无需保存；
        密度仅改表格行高（36/44/32，控件高不随密度变化 A-02）；不允许自定义色值</span>
      </div>

      <el-form label-width="80px" size="small" style="max-width: 460px">
        <el-form-item label="明暗">
          <el-radio-group :value="currentMode" @input="setMode">
            <el-radio-button v-for="m in modes" :key="m.key" :label="m.key">{{ m.name }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="密度">
          <el-radio-group :value="currentDensity" @input="setDensity">
            <el-radio-button v-for="d in densities" :key="d.key" :label="d.key">{{ d.name }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">主题</el-divider>

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
        <el-button size="small" @click="followDefault">清除个人偏好（跟随默认）</el-button>
      </div>
      <p class="muted" style="margin-top: 12px">
        档位说明：推荐/可用/实验/反例为视觉风险标注，全部 12 套均可选用（24 组对比度已实测达标，UID-01 §一.4）。
        会议/大屏模式下主题切换锁定（UID-02 §四）。
      </p>
    </el-card>
  </div>
</template>

<script>
import { THEMES, MODES, DENSITIES, applyTheme, applyMode, applyDensity } from '@/theme/theme'
import { showError } from '@/api/request'

export default {
  name: 'Appearance',
  data() {
    return {
      themes: THEMES,
      modes: MODES,
      densities: DENSITIES,
      currentMode: 'system',
      currentDensity: 'standard'
    }
  },
  computed: {
    defaultTheme() {
      return this.$store.state.defaultTheme || 't1'
    },
    effectiveTheme() {
      return (this.$store.state.user && this.$store.state.user.themeCode) || this.defaultTheme
    }
  },
  mounted() {
    // 从 DOM 读引擎接管后的真实态（防闪脚本 + 引擎收口，不本地猜测）
    try {
      const h = document.documentElement
      this.currentMode = h.getAttribute('data-mode') || 'light'
      this.currentDensity = h.getAttribute('data-density') || 'standard'
    } catch (e) { /* 缺省 */ }
  },
  methods: {
    choose(theme) {
      // 即时生效（无「保存」按钮）+ 服务端持久化（多端一致，FR-SYS-19 R2）
      applyTheme(theme.key)
      this.$store.dispatch('saveTheme', theme.key).catch(showError)
    },
    setMode(mode) {
      applyMode(mode)
      this.currentMode = mode
    },
    setDensity(density) {
      applyDensity(density)
      this.currentDensity = density
    },
    followDefault() {
      this.$store.dispatch('resetTheme').then(() => {
        this.$message.success('已跟随默认')
      }).catch(showError)
    }
  }
}
</script>

<style scoped>
.muted { color: var(--ink-3); font-size: 12px; margin-left: 12px; font-weight: 400; }
</style>
