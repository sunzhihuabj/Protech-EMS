/**
 * 运行时主题（UID-04 §三 · D-UI-01）：CSS 变量令牌层 + ProtechTheme 运行时引擎。
 * 旧 link 切换链路已废弃（D-UI-01 否决预编译 CSS 切 link：闪烁/无 180ms 过渡/包体超重）。
 * 兼容旧调用：applyTheme(themeKey) 保留；新增 applyMode/applyDensity（明暗与密度三档）。
 * 偏好持久化：引擎写 localStorage(protech:ui:pref:v1) + 多标签 storage 同步；
 *            服务端 sys_user.theme_code 双写由 store.saveTheme 完成（多端一致）。
 */
import tokens from './tokens.json'
import ProtechTheme from './index'

/** 12 套主题元数据（名称/档位/主色梯度，供外观面板渲染；色值唯一来源 tokens.json） */
export const THEMES = tokens.themes.map(t => ({
  key: t.key,
  name: t.name,
  grade: t.grade,
  p500: t.pri['500'],
  p800: t.pri['800']
}))

export const MODES = [
  { key: 'light', name: '亮色' },
  { key: 'dark', name: '暗色' },
  { key: 'system', name: '跟随系统' }
]

export const DENSITIES = [
  { key: 'standard', name: '标准（行高 36）' },
  { key: 'comfortable', name: '舒适（行高 44）' },
  { key: 'compact', name: '紧凑（行高 32）' }
]

/** 兼容旧 API：切换主题（persist 参数保留以兼容调用点，持久化由引擎统一收口） */
export function applyTheme(themeKey, persist = true) {
  if (ProtechTheme && ProtechTheme.setTheme) {
    ProtechTheme.setTheme(themeKey, { defer: 120 })
  }
}

/** 明暗模式：light / dark / system（UID-04 §三，禁止业务层直写 dataset） */
export function applyMode(mode) {
  if (ProtechTheme && ProtechTheme.setMode) {
    ProtechTheme.setMode(mode)
  }
}

/** 密度：standard(36) / comfortable(44) / compact(32)，仅改行高与单元格留白（UID-00 A-02） */
export function applyDensity(density) {
  if (ProtechTheme && ProtechTheme.setDensity) {
    ProtechTheme.setDensity(density)
  }
}

/** 清除本地偏好并回到引擎默认（t1 + system + standard）；服务端偏好由调用方另行清空 */
export function resetLocalTheme(defaultTheme) {
  try { localStorage.removeItem('protech:ui:pref:v1') } catch (e) { /* 无痕模式忽略 */ }
  applyTheme(defaultTheme || 't1')
  applyMode('system')
  applyDensity('standard')
}

export function currentTheme() {
  if (ProtechTheme && ProtechTheme.get) {
    return ProtechTheme.get().theme
  }
  return document.documentElement.getAttribute('data-theme') || 't1'
}

export default { applyTheme, applyMode, applyDensity, resetLocalTheme, currentTheme, THEMES, MODES, DENSITIES }
