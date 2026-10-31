# SPRINT UID-R1 · 主题基座重建（D-UI-01 全量落地）

> 日期：2026-10-31 ｜ 依据：UID-00 §三 D-UI-01/04、UID-01 全卷、UID-04 §一/二/三/八
> 状态：✅ 完成（build 绿 + 运行时引擎验证 + 暗态视觉确认）

## 一、整改内容（旧实现 → 新规范）

| 项 | 旧实现（废弃） | 新实现（UID 口径） |
|---|---|---|
| 主题机制 | build-themes.mjs 预编译 12 份 CSS + link 切换（D-UI-01 明令否决：闪烁/无过渡/包体重） | **CSS 变量令牌层**（tokens.json 24 组）+ ProtechTheme 运行时引擎，只改 `html[data-theme][data-mode]` |
| Element 底座 | element-ui/lib/theme-chalk 原蓝 | **L0 中性底座**（element-neutral.scss，$--color-primary=#5F656B 等按 UID-04 §一） |
| 组件接令牌 | 无（预编译静态色） | **L2 覆盖层**（protech-element-bridge.css，24 组件全量接线：Button/Input/Select/DatePicker/Table/Dialog/Tag/Progress/Steps/Tooltip 等，含 --pt-accent-graphic 派生别名、固定列暗态白条修复、焦点环双环） |
| 防闪 | 只写 data-theme（旧 storageKey） | protech-theme-init 压缩版内联：**data-theme+mode+density+contrast + colorScheme**（UID-04 §三 6 条硬约束） |
| 密度 | 无 | standard 36 / comfortable 44 / compact 32（--row-h/--row-h-head 驱动单元格 padding=(行高−22)/2） |
| 明暗 | 无 | light/dark/system（matchMedia 同步解析，暗态例外按 UID-01 §八：主按钮描边微抬、选中竖条 pri-300） |
| 外观页 | 仅 12 主题卡 | 主题卡 + 明暗三态 + 密度三态（API: setTheme/setMode/setDensity，业务层禁直写 dataset） |
| 资产来源 | docs/tokens.reference.json 自建 | **RPD 包唯一来源**：项目主题风格版本/tokens.json + theme-engine 四件套（原样引入，不改值） |

## 二、验证

| 项 | 结果 |
|---|---|
| vite build | ✓ 5.6s（element theme-chalk SCSS 编译通过） |
| 运行时引擎 | ✓ window.ProtechTheme 接管；t1 亮/暗 surface #FFFFFF/#1D1F20；--pt-accent-graphic 亮=pri-500 暗=pri-300 ✓；colorScheme 同步 ✓ |
| 视觉 | ✓ 暗态/亮态截图确认：主按钮令牌色、无蓝残留、卡片 L1 描边 |
| 令牌纪律 | ✓ 零新增 hex（仅 --pt-accent-graphic 派生别名，UID-04 §八备案）；跨层引用单向 |

## 三、与 RPD 条款对应
D-UI-01（实现路线）→ 全量；D-UI-04（24 组灰度）→ 24 组就绪；UID-01 §一—§八 → 令牌/bridge 落地；UID-04 §三（运行时）→ 引擎接管 + storage 多标签同步；UID-04 §六.1（焦点环）→ bridge 末段。

## 四、遗留
- base.css/MainLayout 内个别硬编码色随 R2 布局重构清理
- protech-components.css §2.1 的 5 条 --el-* 死代码与 README Element Plus 口径（G-5）→ S0 清理项，不影响运行
