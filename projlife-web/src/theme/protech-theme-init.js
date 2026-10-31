/* ============================================================================
   Protech 外观设置 · 首屏防闪烁内联片段  v1.0.0
   ----------------------------------------------------------------------------
   用途：复制到业务方 index.html 的 <head> 里，作为【内联】<script>，位置必须在
        所有 <link rel="stylesheet"> 与任何可见节点【之前】。
   规范依据：spec/phase2-ux.md §5.2（6 条硬约束）+ §5.3（优先级）+ §5.4（逐字段兜底）

   6 条硬约束（逐条自查）
   1. 内联 <script>，不是 src；排在所有 stylesheet 之前             → 复制位置即可满足
   2. 同步执行：无 async / defer / type=module、无 DOMContentLoaded、
      无 fetch / XHR / await / rAF                                 → 下面代码里没有这些
   3. minified ≤700 字节、耗时 <1ms（1×getItem + 1×parse + 4×dataset + 1×matchMedia）
   4. 只写 documentElement 属性；不读 DOM 结构、不建节点、不碰 body、不依赖 class
   5. 整段 try/catch，异常静默忽略回落 CSS 默认；绝不 throw、绝不 console.warn
      （内网旧浏览器下控制台噪音会被当成故障上报）
   6. 必须同时设 style.colorScheme，否则暗色下滚动条/原生控件仍白 → 第二处「白闪」

   行为：读 localStorage → 逐字段校验回落 → 解析 mode（system 用 matchMedia 同步解析）
        → 写 html[data-theme / data-mode / data-density / data-contrast] + colorScheme
   注意：data-mode 写的是【解析后的结果】(light|dark)，不是 "system"；
        用户「跟随系统」的意图保存在 localStorage 里，由运行时 JS 负责再解析。
   ========================================================================== */


/* ---------------------------------------------------------------------------
   [A] 可直接粘贴的压缩版 —— 下面这一整段（单行）就是全部内容
   ------------------------------------------------------------------------ */

try{var s=localStorage.getItem("protech:ui:pref:v1"),p=s?JSON.parse(s):{},h=document.documentElement,q=matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light",m=p.v>1?"system":(p.mode=="light"||p.mode=="dark"||p.mode=="system"?p.mode:"system"),t=/^t(1[0-2]|[1-9])$/.test(p.theme)?p.theme:"t1",d=/^(compact|comfortable|standard|default)$/.test(p.density)?(p.density=="default"?"standard":p.density):"standard",c=p.contrast=="high"?"high":"normal",r=m=="system"?q:m;h.dataset.theme=t;h.dataset.mode=r;h.dataset.density=d;h.dataset.contrast=c;h.style.colorScheme=r}catch(e){}

/* ---------------------------------------------------------------------------
   [B] 注释版 —— 与 [A] 语义完全等价，仅供阅读/评审，不要直接粘贴这一版
       （若粘贴，需自行压成一行；带注释会超过 700B）
   ------------------------------------------------------------------------ */
/*
try {
  // —— 1. 读存储（唯一一次 getItem）——
  var raw = localStorage.getItem("protech:ui:pref:v1");
  var pref = raw ? JSON.parse(raw) : {};

  // —— 2. documentElement（只碰这一个节点）——
  var html = document.documentElement;

  // —— 3. 同步解析系统明暗（无 await、无 rAF）——
  var sysMode = matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";

  // —— 4. 逐字段校验：非法字段只回落自己，不整对象清空 ——
  // 4.1 mode：只接受 light / dark / system；读到更高版本(v>1)时强制走默认 system
  var mode =
    pref.v > 1
      ? "system"
      : (pref.mode === "light" || pref.mode === "dark" || pref.mode === "system")
        ? pref.mode
        : "system";                       // 默认 跟随系统（phase2 §5.3）

  // 4.2 theme：12 个合法 key 用正则判定（比数组 indexOf 省字节）
  //     t1–t9 与 t10–t12 两段，等价于 /^t(?:[1-9]|1[0-2])$/
  var theme = /^t(1[0-2]|[1-9])$/.test(pref.theme) ? pref.theme : "t1";   // 默认 t1 深岩青

  // 4.3 density：standard(36) / comfortable(44) / compact(32)；兼容 phase2 §5.1 的旧写法 "default"
  //     ⚠ comfortable 必须认，否则用户存了 44px、首帧却按 36px 画，脚本接管后行高从 36 跳到 44
  var density = /^(compact|comfortable|standard|default)$/.test(pref.density)
    ? (pref.density === "default" ? "standard" : pref.density)
    : "standard";

  // 4.4 contrast：只有 high 是特殊的，其余一律 normal
  var contrast = pref.contrast === "high" ? "high" : "normal";

  // —— 5. system 在读取时同步解析成结果 ——
  var resolved = mode === "system" ? sysMode : mode;

  // —— 6. 写属性：先 CSS 变量，后 colorScheme（第 6 条约束）——
  html.dataset.theme    = theme;
  html.dataset.mode     = resolved;    // 注意：写结果，不写 "system"
  html.dataset.density  = density;
  html.dataset.contrast = contrast;
  html.style.colorScheme = resolved;   // 不设 → 暗色下滚动条/原生控件白闪

  // —— 7. 任何异常静默：不 throw、不 warn、不 console ——
} catch (e) {}
*/

/* ---------------------------------------------------------------------------
   复制示例（整块贴进 index.html 的 <head>，放在所有 stylesheet 之前）：

     <!DOCTYPE html>
     <html lang="zh-CN">
     <head>
       <meta charset="utf-8" />
       <title>Protech</title>
       <script>try{var s=localStorage.getItem("protech:ui:pref:v1"),p=s?JSON.parse(s):{},h=document.documentElement,q=matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light",m=p.v>1?"system":(p.mode=="light"||p.mode=="dark"||p.mode=="system"?p.mode:"system"),t=/^t(1[0-2]|[1-9])$/.test(p.theme)?p.theme:"t1",d=/^(compact|comfortable|standard|default)$/.test(p.density)?(p.density=="default"?"standard":p.density):"standard",c=p.contrast=="high"?"high":"normal",r=m=="system"?q:m;h.dataset.theme=t;h.dataset.mode=r;h.dataset.density=d;h.dataset.contrast=c;h.style.colorScheme=r}catch(e){}</script>
       <link rel="stylesheet" href="./element-plus/index.css" />
       <link rel="stylesheet" href="./theme-engine/protech-theme.css" />
       <link rel="stylesheet" href="./theme-engine/protech-components.css" />
     </head>

   ⚠ 三条不可省的顺序要求
   1) 内联脚本必须在【所有】stylesheet 之前 —— 否则首帧已经用默认值画过一遍 = 白闪。
   2) protech-theme.css 必须在 element-plus 的 index.css【之后】—— 否则 --el-* 覆盖不到。
   3) protech-components.css 必须最后 —— 它覆盖 --row-h 等组件级约定。

   ⚠ CSP 场景
   若内网 CSP 禁止 inline-script，本片段会被拦掉：此时依赖 protech-theme.css 的
   `html:not([data-mode])` + `@media (prefers-color-scheme: dark)` 兜底块（见 README「已知坑 3」）。
   禁止改用 visibility:hidden + 脚本解锁 —— 脚本失败就整页空白，比闪一下严重得多。
   ------------------------------------------------------------------------ */
