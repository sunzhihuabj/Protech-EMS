/**
 * 主题引擎接入层（UID-04 §三）：protech-theme.js 为 UMD/IIFE，挂 window.ProtechTheme。
 * 页面禁止直接写 documentElement.dataset（UID-04 §三"唯一入口"），统一经本模块。
 */
import './protech-theme.js'

const ProtechTheme = typeof window !== 'undefined' && window.ProtechTheme

if (!ProtechTheme) {
  // 引擎加载失败时兜底：防闪脚本已写好首帧属性，这里只保证调用不炸
  console.error('[theme] ProtechTheme 引擎未加载，主题切换不可用（兜底为防闪脚本首帧值）')
}

export default ProtechTheme || {
  setTheme() {}, setMode() {}, setDensity() {}, setContrast() {},
  subscribe() { return () => {} },
  get() { return {} }
}
