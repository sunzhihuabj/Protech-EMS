/**
 * 运行时主题切换（03 册 §6A：预编译包换 <link> href，零编译零接口）。
 * 偏好持久化双写：localStorage（首帧防闪烁）+ 服务端 sys_user.theme_code（多端一致）。
 */
import manifest from './themes.json'

const STORAGE_KEY = manifest.storageKey || 'projlife:ui:pref:v1'
const LINK_ID = 'pl-theme-css'

function readPref() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const pref = raw ? JSON.parse(raw) : {}
    return /^t(1[0-2]|[1-9])$/.test(pref.theme) ? pref.theme : manifest.defaultTheme || 't1'
  } catch (e) {
    return manifest.defaultTheme || 't1'
  }
}

function writePref(theme) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ v: 1, theme, updatedAt: Date.now() }))
  } catch (e) {
    /* 无痕模式等存储失败：功能不阻断，仅不记忆 */
  }
}

function ensureStylesheet(theme) {
  let link = document.getElementById(LINK_ID)
  if (!link) {
    link = document.createElement('link')
    link.id = LINK_ID
    link.rel = 'stylesheet'
    document.head.appendChild(link)
  }
  const target = `/theme/${theme}.css`
  if (!link.getAttribute('href').endsWith(`${theme}.css`)) {
    link.setAttribute('href', target)
  }
  document.documentElement.setAttribute('data-theme', theme)
}

/** 切换主题（即时生效）；persist=true 时写本地缓存 */
export function applyTheme(theme, persist = true) {
  if (!/^t(1[0-2]|[1-9])$/.test(theme)) {
    return readPref()
  }
  ensureStylesheet(theme)
  if (persist) {
    writePref(theme)
  }
  return theme
}

/** 清除本地偏好并回到系统默认 */
export function resetLocalTheme(defaultTheme) {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (e) { /* 忽略 */ }
  ensureStylesheet(defaultTheme || manifest.defaultTheme || 't1')
}

export function currentTheme() {
  return document.documentElement.getAttribute('data-theme') || readPref()
}

export default { applyTheme, resetLocalTheme, currentTheme, manifest }
