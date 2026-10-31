/* ============================================================================
   Protech 主题引擎 · 无依赖运行时  v1.0.0
   ----------------------------------------------------------------------------
   引入方式（两种都支持，禁止 ESM 语法与顶层 await，file:// 双击也能跑）
     1) <script src="./protech-theme.js"></script>       → window.ProtechTheme
     2) const PL = require('./protech-theme.js')         → CJS
     3) 打包器里 `import PL from './protech-theme.js'`   → 走 CJS 互操作（同 2）

   色值唯一来源：visual/spec/tokens.json（由 visual/_tokens.py 与
   protech-theme.css 同源）。本文件里【没有】手填的对比度数字：
   c500 / c800 在初始化时用 WCAG 相对亮度公式实算一次。

   存储：key = "protech:ui:pref:v1"
     { v:1, theme:"t1", mode:"light|dark|system", density:"standard|compact",
       contrast:"normal|high", seenAppearance:false, updatedAt:1699... }

   与规范的三处显式取舍（已同步给 team-lead，见 README「已知坑」）
     · density 三档 standard(36) / comfortable(44) / compact(32)；phase2 §5.1 里的
       旧写法 "default" 读时归一为 "standard"。写出去的永远是三个规范值之一。
       ⚠ comfortable 必须留在取值域里：演示页只挂 2 个入口，但上线要按岗位开放 44px。
     · 系统明暗切换（matchMedia change）只重绘不写存储，订阅回调 source 仍取 'local'
       —— 严格守住 source ∈ {'local','storage','init'} 这三元约定
     · 读到更高版本存储（v>1）时既不读字段也不写回：persisted 返回 false
   ========================================================================== */
;(function (global) {
  'use strict';

  /* ---------------------------------------------------------------- 常量 */

  var VERSION = '1.0.0';
  var STORAGE_KEY = 'protech:ui:pref:v1';
  var SCHEMA_VERSION = 1;
  var SWITCH_MS = 180;          // phase2 §3②：换色统一 180ms
  var DEFER_MS = 250;           // phase2 §6.2：方向键连扫防抖，跳色收敛到 ≤1 次

  var DEFAULTS = {
    theme: 't1',                // phase2 §5.3：默认 t1 深岩青
    mode: 'system',             // phase2 §5.3：默认跟随系统
    density: 'standard',
    contrast: 'normal'
  };

  var MODES = ['light', 'dark', 'system'];
  var DENSITIES = ['standard', 'comfortable', 'compact'];   // 三档：36 / 44 / 32
  var CONTRASTS = ['normal', 'high'];

  /* 档位 → 机器可读 key（与 panel-metrics.json 的 meta.gradeKeys 一致）。
     面板拿它拼 class / data-grade，不要拿中文串做 switch。 */
  var GRADE_KEYS = { '推荐': 'recommended', '可用': 'usable', '实验': 'experimental', '反例': 'counter' };

  /* -------------------------------------------------- 主题元数据（12 套）
     数据来源与权威性（改档位/色值前先读这段）：
     · 色值 p200/p300/p500/p600/p800 + darkSurface：取自 spec/tokens.json 与
       protech-theme.css（运行时真正渲染的那份）。**12 套 = 全量，无 disabled，无隐藏。**
     · chroma（C*，Lab 彩度）、grade、order：镜像自 spec/panel-metrics.json
       （该文件 19:28 版已补齐 grade / order / chroma / residual）。
       档位由脚本 derive() 推导，前端**只读不推**：不要自己算角差、不要自己写档位表，
       更不要按 grade 生成 disabled（P1 全可选）。
     · ⚠ 判「中性 / 低饱和」必须用 **C\*** 而不是 HSL 饱和度 S：
         C* < 6   → 中性（主色即中性、主操作降级 → 实验）   t11 = 0.0、t7 = 5.6
         6 ≤ C* < 14 → 低饱和（→ 可用）                     t5 = 13.9
         C* ≥ 14  → 有彩
       不能用 S 的原因：t7 的 S 是 11.5%、t5 是 23.7%，只差 2 倍就分不开；而且 HSL
       在低明度下系统性高估彩度（t7 的 500 明度只有 12%，S 被放大才显出 11.5%，
       其实它就是灰）。C* 的 5.6 vs 13.9 才和眼睛看到的一致。
     · ⚠ 反例判据与网格排布都有一道「无彩门」：用 json 的 **`hueUsable`** 字段判，
       不要用 `hue === 0` —— t11 的 hue 恰好是 0 能混过去，但 **t7 的 hue 是 208.0**
       （#3A4249 带 11.5% 蓝，数学上算得出 208°，C* 只有 5.6，它就是灰）。
       用 `hue === 0` 会漏掉 t7：t7 与 t1 差 18.6°，换个行序就会误报成"相邻破约"，
       而且是"越排越乱却查不出原因"那种误报。
       正确写法：`if (!a.hueUsable || !b.hueUsable) skip`。
       不自己写 C*<6 阈值，是因为一旦和 _calc_panel.py 里的判定分叉，两份阈值迟早对不上。
       t11 必须是「实验」，**不得因 danger 角差 0.0° 被判成反例** —— 回归项。
     · order：卡片网格行序（4 列 × 3 行），镜像 panel-metrics.json 的 order 字段。
       生成器已内置 orderCheck 守卫（违反即 raise），json 的 meta.orderCheck 里有
       minOrth 30.5° / minDiag 17.9° / violations []。
       唯一硬约束：t6 与 t9 不得相邻（全表最近 22.5°）。
     · hueActual：实测色相（由 pri-500 hex 反算），与 hue（设计角）不是一回事：
       t1 设计 H210、实测 H189.4。卡片上的色相标记用 hueActual —— 色觉障碍用户
       拿取色器量出来的就是这个数。但注意 **hueActual 对无彩主题无意义**，
       显示前先看 hueUsable。 */

  var TABLE = [
    { key: 't1',  name: '深岩青', grade: '推荐', order: 11, hue: 210, hueActual: 189.4, hueUsable: true,  chroma: 26.1, accent: null,      p200: '#A3CFD6', p300: '#6FB2BD', p500: '#1F7B8C', p600: '#14606F', p800: '#0B3841', ds: '#1D1F20' },
    { key: 't2',  name: '钢蓝',   grade: '推荐', order: 2,  hue: 212, hueActual: 205.8, hueUsable: true,  chroma: 32.7, accent: null,      p200: '#AEC7D9', p300: '#88ACC8', p500: '#2A6B9C', p600: '#245C86', p800: '#183C57', ds: '#1D1E20' },
    { key: 't3',  name: '靛蓝',   grade: '推荐', order: 5,  hue: 228, hueActual: 228, hueUsable: true,  chroma: 49.5, accent: null,      p200: '#B4BCDA', p300: '#919CC9', p500: '#3A4E9E', p600: '#324388', p800: '#202C58', ds: '#1D1E20' },
    { key: 't4',  name: '深青绿', grade: '可用', order: 1,  hue: 180, hueActual: 175.1, hueUsable: true,  chroma: 28.2, accent: null,      p200: '#A5CAC7', p300: '#7AB1AC', p500: '#12736B', p600: '#0F635C', p800: '#0A403C', ds: '#1D2020' },
    { key: 't5',  name: '石青灰', grade: '可用', order: 4,  hue: 196, hueActual: 193, hueUsable: true,  chroma: 13.9, accent: null,      p200: '#BAC8CC', p300: '#9AAEB3', p500: '#4A6E78', p600: '#405F67', p800: '#293E43', ds: '#1D1F20' },
    { key: 't6',  name: '藏紫',   grade: '推荐', order: 3,  hue: 262, hueActual: 255.5, hueUsable: true,  chroma: 41.4, accent: null,      p200: '#C1BAD3', p300: '#A39ABF', p500: '#5B4A8C', p600: '#4E4078', p800: '#33294E', ds: '#1E1D20' },
    { key: 't7',  name: '石墨金', grade: '实验', order: 9,  hue: 0,   hueActual: 208, hueUsable: false, chroma: 5.6,  accent: '#B08A3E', p200: '#B4B7BA', p300: '#919599', p500: '#3A4249', p600: '#32393F', p800: '#202529', ds: '#201D1D' },
    { key: 't8',  name: '墨绿',   grade: '反例', order: 0,  hue: 148, hueActual: 144.6, hueUsable: true,  chroma: 39.5, accent: null,      p200: '#B0CEBC', p300: '#8BB69C', p500: '#2F7D4F', p600: '#286C44', p800: '#1A462C', ds: '#1D201E' },
    { key: 't9',  name: '阳极紫', grade: '推荐', order: 6,  hue: 278, hueActual: 277.7, hueUsable: true,  chroma: 45.7, accent: null,      p200: '#C6B6D0', p300: '#AC93BA', p500: '#6A3E84', p600: '#5B3572', p800: '#3B234A', ds: '#1F1D20' },
    { key: 't10', name: '锰紫',   grade: '推荐', order: 7,  hue: 324, hueActual: 323.4, hueUsable: true,  chroma: 40.5, accent: null,      p200: '#D0B2C4', p300: '#B98EA8', p500: '#823564', p600: '#702E56', p800: '#491E38', ds: '#201D1F' },
    { key: 't11', name: '钨钢',   grade: '实验', order: 10, hue: 0,   hueActual: 0,   hueUsable: false, chroma: 0,    accent: null,      p200: '#ADADAD', p300: '#858585', p500: '#262626', p600: '#212121', p800: '#151515', ds: '#201D1D' },
    { key: 't12', name: '铁锈红', grade: '实验', order: 8,  hue: 20,  hueActual: 19.8,  hueUsable: true,  chroma: 42.2, accent: null,      p200: '#D9BEB1', p300: '#C79F8C', p500: '#9B5431', p600: '#85482A', p800: '#572F1B', ds: '#201E1D' }
  ];

  /* ------------------------------------------------ WCAG 相对亮度 / 对比度 */

  function srgbToLinear(channel) {
    var c = channel / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }

  function hexToRgb(hex) {
    var h = String(hex).replace('#', '').trim();
    if (h.length === 3) {
      h = h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
    }
    var n = parseInt(h, 16);
    return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
  }

  /** WCAG 2.x 相对亮度，取值 0–1 */
  function luminance(hex) {
    var rgb = hexToRgb(hex);
    return 0.2126 * srgbToLinear(rgb.r) + 0.7152 * srgbToLinear(rgb.g) + 0.0722 * srgbToLinear(rgb.b);
  }

  /** 对比度 (L1+0.05)/(L2+0.05)，用于「亮者在上」，与顺序无关 */
  function contrastRatio(a, b) {
    var la = luminance(a);
    var lb = luminance(b);
    var hi = Math.max(la, lb);
    var lo = Math.min(la, lb);
    return (hi + 0.05) / (lo + 0.05);
  }

  function round2(n) {
    return Math.round(n * 100) / 100;
  }

  /* --------------------------------------------------- THEMES：对外元数据
     c500 = pri-500 对白底（#FFFFFF）的对比度  → 亮态卡片「主色 vs 底色」数字
     cDark = pri-300 对【该主题自己的 dark.surface】的对比度 → 暗态卡片数字
       ⚠ 暗态绝不能拿 pri-500 去压暗底：12 套实测只有 1.14–3.51，整排会假性掉档。
         基准必须是 pri-300（phase1 二·8-3），这是 panel-metrics 里
         `dark_300_vs_surface` 的口径，json 里那个 `..._NOT_FOR_TEXT` 字段别用。
       c500/cDark 都由本文件的 hex 实算，所以只要色值更新，数字自动跟着对，不会脱节。
       （面板对外展示仍以 spec/panel-metrics.json 为权威，此处供运行时自检/降级用。）
     c800 = pri-800 对白字的对比度              → 侧栏白字可读性
     darkPriText = 暗态 --pri-text 的实际取值：
       有彩主题取 pri-300；无彩主题 t7/t11 取 pri-200
       （phase1 二·8-3：t11 的 pri-300=4.68 只超门槛 0.18，无余量，故升一档到 pri-200）
     order = 网格行序（0 起），grade 只驱动标注、不驱动 disabled。 */

  var THEMES = TABLE.map(function (t) {
    var isNeutral = t.chroma < 6;                 // C* < 6 → 中性（无彩门）
    var darkPri = isNeutral ? t.p200 : t.p300;    // 暗态 --pri-text 的实际取值
    var cDark = round2(contrastRatio(t.p300, t.ds));
    return {
      key: t.key,
      name: t.name,
      grade: t.grade,
      // gradeKey 供面板拼 class / data 属性用（避免拿中文串做 switch）。
      // 映射与 panel-metrics.json 的 meta.gradeKeys 一致。
      gradeKey: GRADE_KEYS[t.grade] || 'recommended',
      order: t.order,
      hue: t.hue,
      hueActual: t.hueActual,
      // hueUsable=false 表示这是无彩主题（t7/t11），hueActual 只是数学产物、没有知觉意义。
      // 任何"按色相差判断"的逻辑（相邻卡角差、反例角差）都必须先看这个字段 ——
      // 用 `hue === 0` 判会漏掉 t7（其 hueActual = 208）。
      hueUsable: t.hueUsable,
      // hueLabel 取整到整度给卡片显示（H189），但 hueActual 保留 1 位小数 ——
      // 角差复算必须用精确值，否则 30.5° 会被算成 30.0°、与生成器的 orderCheck 对不上。
      hueLabel: isNeutral ? (t.accent ? '无彩色 + 金 H40' : '无彩色 H0') : 'H' + Math.round(t.hueActual),
      chroma: t.chroma,
      satBand: isNeutral ? 'neutral' : (t.chroma < 14 ? 'lowSat' : 'colored'),
      p500: t.p500,
      p600: t.p600,
      p800: t.p800,
      c500: round2(contrastRatio(t.p500, '#FFFFFF')),
      cDark: cDark,
      darkSurface: t.ds,
      // 余量不足：≥4.5 但余量 < 0.1（panel-metrics 的 residualWarning 阈值）
      // 命中时必须显示实测值 + 替代层，禁止只打个"通过"，
      // 也禁止写成 if (theme === 't11') 这种硬编码。
      cDarkTight: (cDark - 4.5) < 0.1,
      darkPriTextContrast: round2(contrastRatio(darkPri, t.ds)),
      c800: round2(contrastRatio(t.p800, '#FFFFFF')),
      accent: t.accent,
      darkPriText: darkPri
    };
  });

  var THEME_INDEX = {};
  for (var i = 0; i < THEMES.length; i++) THEME_INDEX[THEMES[i].key] = THEMES[i];
  function hasTheme(key) {
    return typeof key === 'string' && Object.prototype.hasOwnProperty.call(THEME_INDEX, key);
  }

  function themeMeta(key) {
    var m = THEME_INDEX[key] || THEME_INDEX[DEFAULTS.theme];
    return {
      key: m.key, name: m.name, grade: m.grade, gradeKey: m.gradeKey, order: m.order,
      hue: m.hue, hueActual: m.hueActual, hueUsable: m.hueUsable, hueLabel: m.hueLabel,
      chroma: m.chroma, satBand: m.satBand,
      p500: m.p500, p600: m.p600, p800: m.p800,
      c500: m.c500, cDark: m.cDark, cDarkTight: m.cDarkTight,
      darkPriTextContrast: m.darkPriTextContrast,
      c800: m.c800, darkSurface: m.darkSurface,
      accent: m.accent, darkPriText: m.darkPriText
    };
  }

  /* ------------------------------------------------------ 环境访问（可桩化）
     全部懒解析：调用时才读全局，方便 node 测试里注入最小 stub，
     也避免 SSR/预渲染阶段（无 document）直接炸掉。 */

  function getDoc() {
    try { return typeof document !== 'undefined' && document ? document : null; } catch (e) { return null; }
  }

  function getWin() {
    try { return typeof window !== 'undefined' && window ? window : null; } catch (e) { return null; }
  }

  function getStorage() {
    try {
      if (typeof localStorage !== 'undefined' && localStorage) return localStorage;
    } catch (e) { /* 无痕/被禁时访问本身可能抛错 */ }
    try {
      var w = getWin();
      return w && w.localStorage ? w.localStorage : null;
    } catch (e2) { return null; }
  }

  function mql(query) {
    var w = getWin();
    if (!w || typeof w.matchMedia !== 'function') return null;
    try { return w.matchMedia(query); } catch (e) { return null; }
  }

  function prefersReducedMotion() {
    var m = mql('(prefers-reduced-motion: reduce)');
    return !!(m && m.matches);
  }

  /* ------------------------------------------------------------ 内部状态 */

  var state = {
    theme: DEFAULTS.theme,
    mode: DEFAULTS.mode,
    density: DEFAULTS.density,
    contrast: DEFAULTS.contrast,
    seenAppearance: false,
    updatedAt: 0
  };

  var persisted = true;   // 写入成功与否（无痕/被禁 → false）
  var blocked = false;    // 读到更高版本 v>1 → 不读不写
  var listeners = [];
  var applied = null;     // { theme, mode } 上次真正写进 DOM 的值，用于判断要不要加过渡
  var switchTimer = null;
  var deferTimer = null;
  var mqBound = false;
  var ready = false;

  /* -------------------------------------------------------------- 校验 */

  function normMode(v) {
    return MODES.indexOf(v) >= 0 ? v : null;
  }

  function normDensity(v) {
    if (v === 'default') return 'standard';   // phase2 §5.1 的写法 default ≡ 标准 36
    return DENSITIES.indexOf(v) >= 0 ? v : null;   // standard / comfortable / compact 三档
  }

  function normContrast(v) {
    return CONTRASTS.indexOf(v) >= 0 ? v : null;
  }

  /** system → 用 matchMedia 同步解析成 light|dark */
  function resolveMode(mode) {
    if (mode === 'light' || mode === 'dark') return mode;
    var m = mql('(prefers-color-scheme: dark)');
    return m && m.matches ? 'dark' : 'light';
  }

  /* ---------------------------------------------------------- 存储读写 */

  /**
   * 读存储并【逐字段】校验：非法字段回落默认，合法字段保留。
   * 绝不整对象清空（phase2 §5.4）。
   */
  function readPrefs() {
    var out = { blocked: false, hit: false };
    var rawStr = null;
    var store = getStorage();
    if (!store) return out;
    try { rawStr = store.getItem(STORAGE_KEY); } catch (e) { return out; }
    if (!rawStr) return out;

    var o = null;
    try { o = JSON.parse(rawStr); } catch (e) { return out; }   // JSON 损坏 → 全默认，但不算命中
    if (!o || typeof o !== 'object') return out;

    var v = typeof o.v === 'number' ? o.v : SCHEMA_VERSION;
    if (v > SCHEMA_VERSION) { out.blocked = true; return out; } // 更高版本：不读不写

    out.hit = true;
    out.theme = hasTheme(o.theme) ? o.theme : null;
    out.mode = normMode(o.mode);
    out.density = normDensity(o.density);
    out.contrast = normContrast(o.contrast);
    out.seenAppearance = typeof o.seenAppearance === 'boolean' ? o.seenAppearance : null;
    out.updatedAt = typeof o.updatedAt === 'number' ? o.updatedAt : 0;
    return out;
  }

  function persist() {
    if (blocked) { persisted = false; return false; }
    state.updatedAt = Date.now();
    var store = getStorage();
    if (!store) { persisted = false; return false; }
    try {
      store.setItem(STORAGE_KEY, JSON.stringify({
        v: SCHEMA_VERSION,
        theme: state.theme,
        mode: state.mode,
        density: state.density,
        contrast: state.contrast,
        seenAppearance: state.seenAppearance,
        updatedAt: state.updatedAt
      }));
      persisted = true;
      return true;
    } catch (e) {
      // 无痕模式 / 配额满 / 被策略禁用 → 降级内存态，功能不阻断
      persisted = false;
      return false;
    }
  }

  /* ---------------------------------------------------------- 订阅广播 */

  function notify(source) {
    var payload = get();
    payload.source = source;
    var snapshot = listeners.slice();
    for (var i = 0; i < snapshot.length; i++) {
      try { snapshot[i](payload); } catch (e) { /* 单个订阅者异常不影响其他人 */ }
    }
  }

  /* --------------------------------------------------------- 落 DOM */

  /** 把当前状态写到 <html>；切换时加 .theme-switching（180ms 后移除） */
  function apply() {
    var d = getDoc();
    if (!d) return get();
    var el = d.documentElement;
    if (!el) return get();

    var resolved = resolveMode(state.mode);
    var changed = !!applied && (applied.theme !== state.theme || applied.mode !== resolved);
    var animate = changed && !prefersReducedMotion();

    if (animate && el.classList && typeof el.classList.add === 'function') {
      if (switchTimer) clearTimeout(switchTimer);
      el.classList.add('theme-switching');
      switchTimer = setTimeout(function () {
        switchTimer = null;
        try { if (el.classList) el.classList.remove('theme-switching'); } catch (e) {}
      }, SWITCH_MS);
    }

    try {
      if (el.dataset) {
        el.dataset.theme = state.theme;
        el.dataset.mode = resolved;          // 写解析结果，不写 "system"
        el.dataset.density = state.density;
        el.dataset.contrast = state.contrast;
      } else {
        el.setAttribute('data-theme', state.theme);
        el.setAttribute('data-mode', resolved);
        el.setAttribute('data-density', state.density);
        el.setAttribute('data-contrast', state.contrast);
      }
      if (el.style) el.style.colorScheme = resolved;   // 必须：滚动条/原生控件
    } catch (e) { /* 静默 */ }

    applied = { theme: state.theme, mode: resolved };
    return get();
  }

  /**
   * 统一收口：先广播（勾选态立即跟随），再按需 apply（视觉可延后）。
   * @param {number} defer 毫秒；>0 表示合并连续切换，只在停顿后应用一次
   * @param {string} source 'local' | 'storage' | 'init'
   */
  function commit(defer, source) {
    persistedFlagRefresh();
    notify(source || 'local');
    if (defer && defer > 0) {
      if (deferTimer) clearTimeout(deferTimer);
      deferTimer = setTimeout(function () {
        deferTimer = null;
        apply();
      }, defer);
      return;
    }
    if (deferTimer) { clearTimeout(deferTimer); deferTimer = null; }
    apply();
  }

  function persistedFlagRefresh() {
    // 存储被禁时也要在 get() 里持续暴露 persisted:false；此处只在首次探测后保持真实值
    if (blocked) persisted = false;
  }

  /* --------------------------------------------------------------- API */

  function get() {
    return {
      theme: state.theme,
      mode: state.mode,
      resolvedMode: resolveMode(state.mode),
      density: state.density,
      contrast: state.contrast,
      themeMeta: themeMeta(state.theme),
      persisted: persisted && !blocked,
      seenAppearance: state.seenAppearance,
      updatedAt: state.updatedAt
    };
  }

  /**
   * 切换主题。
   * @param {string} key 12 个合法 key 之一；非法 key = 空操作（不改状态、不写存储）
   * @param {{defer?: number}} [opts] defer 毫秒；键盘方向键连扫请传 250
   */
  function setTheme(key, opts) {
    if (!hasTheme(key)) return get();
    var defer = opts && typeof opts.defer === 'number' && opts.defer >= 0 ? opts.defer : 0;
    state.theme = key;
    persist();
    commit(defer, 'local');
    return get();
  }

  function setMode(mode) {
    var m = normMode(mode);
    if (!m) return get();
    state.mode = m;
    persist();
    commit(0, 'local');
    return get();
  }

  function setDensity(density) {
    var d = normDensity(density);
    if (!d) return get();
    state.density = d;
    persist();
    commit(0, 'local');
    return get();
  }

  function setContrast(contrast) {
    var c = normContrast(contrast);
    if (!c) return get();
    state.contrast = c;
    persist();
    commit(0, 'local');
    return get();
  }

  /** 面板「已看过外观设置」红点（phase2 §1），只写存储 + 广播，不动 DOM 属性 */
  function setSeenAppearance(seen) {
    state.seenAppearance = !!seen;
    persist();
    commit(0, 'local');
    return get();
  }

  /**
   * 【构建期/开发期对账，不要在生产运行时调】
   * 把内嵌的 THEMES 与 spec/panel-metrics.json 逐字段比对，返回差异清单。
   * 存在意义：THEMES 里的 grade / order / chroma / 对比度是【镜像】——
   *   本引擎必须能在 file:// 双击运行、不能 import JSON、不能 fetch，
   *   所以没法"直接读 json"。既然一定要镜像，就把"两处会不会漂"变成可机器检测的：
   * 每次 visual-arch 重跑 panel-metrics.json 之后跑一次 verifyMetrics，0 diff 才算对齐。
   *
   * @param {object} metrics 就是 require('spec/panel-metrics.json') 的对象
   * @returns {{ok:boolean, diffs:Array<{key:string,field:string,got:any,expect:any}>}}
   */
  function verifyMetrics(metrics) {
    var diffs = [];
    if (!metrics || !metrics.themes || !metrics.themes.length) {
      return { ok: false, diffs: [{ key: '*', field: 'metrics', got: typeof metrics, expect: 'panel-metrics.json 对象' }] };
    }
    var map = {};
    for (var j = 0; j < metrics.themes.length; j++) map[metrics.themes[j].key] = metrics.themes[j];

    THEMES.forEach(function (t) {
      var m = map[t.key];
      if (!m) { diffs.push({ key: t.key, field: '*', got: 'THEMES 有', expect: 'json 缺失' }); return; }
      if (m.grade !== t.grade) diffs.push({ key: t.key, field: 'grade', got: t.grade, expect: m.grade });
      if (m.gradeKey && m.gradeKey !== t.gradeKey) diffs.push({ key: t.key, field: 'gradeKey', got: t.gradeKey, expect: m.gradeKey });
      if (typeof m.order === 'number' && m.order !== t.order) diffs.push({ key: t.key, field: 'order', got: t.order, expect: m.order });
      if (typeof m.chroma === 'number' && Math.abs(m.chroma - t.chroma) > 0.01) diffs.push({ key: t.key, field: 'chroma', got: t.chroma, expect: m.chroma });
      if (typeof m.hueUsable === 'boolean' && m.hueUsable !== t.hueUsable) diffs.push({ key: t.key, field: 'hueUsable', got: t.hueUsable, expect: m.hueUsable });
      // hueActual 必须逐位一致（0.1 容差）：整数化会丢精度，让角差复算与 orderCheck 分叉
      if (typeof m.hue === 'number' && Math.abs(m.hue - t.hueActual) > 0.1) diffs.push({ key: t.key, field: 'hueActual', got: t.hueActual, expect: m.hue });
      if (typeof m.name === 'string' && m.name !== t.name) diffs.push({ key: t.key, field: 'name', got: t.name, expect: m.name });
      var c = m.contrast || {};
      if (typeof c.light_500_vs_white === 'number' && Math.abs(c.light_500_vs_white - t.c500) > 0.005) {
        diffs.push({ key: t.key, field: 'c500', got: t.c500, expect: c.light_500_vs_white });
      }
      if (typeof c.dark_300_vs_surface === 'number' && Math.abs(c.dark_300_vs_surface - t.cDark) > 0.005) {
        diffs.push({ key: t.key, field: 'cDark', got: t.cDark, expect: c.dark_300_vs_surface });
      }
      // 余量不足主题的替代层：我的 darkPriTextContrast 必须等于 json 的 altContrast。
      // 只在 tight 时比 —— 非 tight 主题的 altToken 是"再亮一档"，与本引擎的
      // darkPriText（真正用于渲染的那一档）不是同一个概念，比了会误报。
      var res = m.residual || {};
      if (res.tight === true) {
        if (res.altToken && res.altToken !== t.darkPriText) {
          diffs.push({ key: t.key, field: 'altToken', got: t.darkPriText, expect: res.altToken });
        }
        if (typeof res.altContrast === 'number' && Math.abs(res.altContrast - t.darkPriTextContrast) > 0.02) {
          diffs.push({ key: t.key, field: 'altContrast', got: t.darkPriTextContrast, expect: res.altContrast });
        }
      }
    });

    // 反向：json 里有、THEMES 里没有
    for (var k = 0; k < metrics.themes.length; k++) {
      if (!hasTheme(metrics.themes[k].key)) {
        diffs.push({ key: metrics.themes[k].key, field: '*', got: 'json 有', expect: 'THEMES 缺失' });
      }
    }

    // 生成器自带的行序守卫：json 自己也承认破了约，就没必要再比字段值了。
    // 这条拦的是"行序被人手工改乱、但 verifyMetrics 只比字段因此看起来 0 diff"的场景 ——
    // json 的 orderCheck 由 _calc_panel.py 每次重算，违反即 raise，这里是第二道。
    var oc = metrics.meta && metrics.meta.orderCheck;
    if (oc && oc.ok !== true) {
      diffs.push({
        key: '*', field: 'orderCheck', got: 'ok',
        expect: 'violations ' + JSON.stringify(oc.violations) + ' / minOrth ' + oc.minOrth + '° / minDiag ' + oc.minDiag + '°'
      });
    }

    return { ok: diffs.length === 0, diffs: diffs };
  }

  /** 重置为默认外观：t1 · 跟随系统 · 标准 · 高对比关 */
  function reset() {
    state.theme = DEFAULTS.theme;
    state.mode = DEFAULTS.mode;
    state.density = DEFAULTS.density;
    state.contrast = DEFAULTS.contrast;
    persist();
    commit(0, 'local');
    return get();
  }

  /**
   * 订阅状态变化。
   * 注册时会【同步回调一次】 source:'init' 做初始值回放 —— 组件挂载即可拿到现值，
   * 不必先 get() 再 subscribe()（两者之间有 1 帧窗口会丢变更）。
   * @param {(p: object) => void} fn 收到 {...get(), source:'local'|'storage'|'init'}
   * @returns {() => void} unsubscribe
   */
  function subscribe(fn) {
    if (typeof fn !== 'function') return function () {};
    listeners.push(fn);
    var initial = get();
    initial.source = 'init';
    try { fn(initial); } catch (e) { /* 单个订阅者异常不影响其他人 */ }
    return function () {
      var i = listeners.indexOf(fn);
      if (i >= 0) listeners.splice(i, 1);
    };
  }

  /* ------------------------------------------------- 多标签页 / 系统明暗 */

  /** 只读远端、绝不回写 → 避免 storage 回环风暴（phase2 §5.4） */
  function onStorage(e) {
    if (!e || e.key !== STORAGE_KEY) return;

    if (e.newValue == null) {
      // 远端清空 → 回默认（不写回，保持「无 key」状态）
      state.theme = DEFAULTS.theme;
      state.mode = DEFAULTS.mode;
      state.density = DEFAULTS.density;
      state.contrast = DEFAULTS.contrast;
      state.seenAppearance = false;
      applied = null;                 // 视为首次应用，不加过渡
      apply();
      notify('storage');
      return;
    }

    var o = null;
    try { o = JSON.parse(e.newValue); } catch (err) { return; }
    if (!o || typeof o !== 'object') return;
    var v = typeof o.v === 'number' ? o.v : SCHEMA_VERSION;
    if (v > SCHEMA_VERSION) {           // 更高版本：装作没看见
      blocked = true;
      persisted = false;
      return;
    }
    blocked = false;

    // 逐字段：远端缺字段就保持本地现值，不因为对方少写一个字段就把本地清空
    if (hasTheme(o.theme)) state.theme = o.theme;
    var m = normMode(o.mode);
    if (m) state.mode = m;
    var dn = normDensity(o.density);
    if (dn) state.density = dn;
    var ct = normContrast(o.contrast);
    if (ct) state.contrast = ct;
    if (typeof o.seenAppearance === 'boolean') state.seenAppearance = o.seenAppearance;
    if (typeof o.updatedAt === 'number') state.updatedAt = o.updatedAt;
    persisted = true;

    apply();                            // 同步时允许走 180ms 过渡
    notify('storage');
  }

  /** OS 明暗变化：仅当 mode==='system' 时重绘（不写存储） */
  function onSystemSchemeChange() {
    if (state.mode !== 'system') return;
    apply();
    notify('local');
  }

  function bind() {
    if (ready) return;
    ready = true;

    var w = getWin();
    if (w && typeof w.addEventListener === 'function') {
      w.addEventListener('storage', onStorage);
    }

    var m = mql('(prefers-color-scheme: dark)');
    if (m && !mqBound) {
      mqBound = true;
      if (typeof m.addEventListener === 'function') m.addEventListener('change', onSystemSchemeChange);
      else if (typeof m.addListener === 'function') m.addListener(onSystemSchemeChange);
    }

    // 初始化：读存储 → 逐字段回落 → 同步落 DOM（首次不加过渡）
    var prefs = readPrefs();
    blocked = !!prefs.blocked;
    if (blocked) {
      persisted = false;
    } else if (prefs.hit) {
      if (prefs.theme) state.theme = prefs.theme;
      if (prefs.mode) state.mode = prefs.mode;
      if (prefs.density) state.density = prefs.density;
      if (prefs.contrast) state.contrast = prefs.contrast;
      if (prefs.seenAppearance !== null) state.seenAppearance = prefs.seenAppearance;
      if (prefs.updatedAt) state.updatedAt = prefs.updatedAt;
    }

    apply();
    // 不在这里广播：此刻还没有任何订阅者。初始值由 subscribe() 同步回放（source:'init'）
  }

  /* ------------------------------------------------------------- 出口 */

  var API = {
    version: VERSION,
    STORAGE_KEY: STORAGE_KEY,
    DEFAULTS: { theme: DEFAULTS.theme, mode: DEFAULTS.mode, density: DEFAULTS.density, contrast: DEFAULTS.contrast },
    THEMES: THEMES,

    get: get,
    setTheme: setTheme,
    setMode: setMode,
    setDensity: setDensity,
    setContrast: setContrast,
    setSeenAppearance: setSeenAppearance,
    reset: reset,
    subscribe: subscribe,
    apply: apply,

    // 构建期对账：verifyMetrics(require('spec/panel-metrics.json')) → {ok, diffs}
    // 生产运行时不要调（只是内嵌镜像与 json 的一致性检查）
    verifyMetrics: verifyMetrics,

    // 供面板做「构建缺失」嗅探（phase2 §5.4 最后一行）
    isCssLoaded: function () {
      var d = getDoc();
      var w = getWin();
      if (!d || !w || typeof w.getComputedStyle !== 'function') return true; // 无 DOM 时不误报
      try {
        var v = w.getComputedStyle(d.documentElement).getPropertyValue('--pri-500');
        return !!(v && v.trim());
      } catch (e) { return true; }
    }
  };

  // 立即接管：读存储 + 落 DOM + 绑事件
  bind();

  global.ProtechTheme = API;
  if (typeof module !== 'undefined' && module && module.exports) module.exports = API;
  if (typeof define === 'function' && define.amd) define(function () { return API; });

})(typeof globalThis !== 'undefined' ? globalThis : typeof self !== 'undefined' ? self : this);
