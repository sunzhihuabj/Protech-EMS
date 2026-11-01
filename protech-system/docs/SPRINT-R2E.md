# SPRINT R2-E · Web 深化 + 12 主题系统（FR-M00-014）

> R2 深化切片 5（收尾）｜分支 protech-v2｜2026-11-02
> DoD：12 主题切换 + AI 助手/报表中心/BV-01/BV-03 页面上线；浏览器实测通过。

## 交付内容

### FR-M00-014 主题系统（12 主题 × 明暗）
- `visual/` 资产 tokens.json（12 主题：深岩青/钢蓝/靛蓝/深青绿/石青灰/藏紫/石墨金/墨绿/阳极紫/锰紫/钨钢/铁锈红）直接作为**单一事实源**导入 `apps/web/src/theme/`
- ThemeSwitcher：ColorPicker 按 pri.500 选中主题 + 明暗 Switch；CSS 变量注入 `--pt-pri-*`/`--pt-neu-*`
- antd 联动：colorPrimary=主题 pri.500；暗色=darkAlgorithm（默认深色导航+浅色内容 07 册 §五）

### 新页面（07 册路由）
| 路由 | 页面 | 数据源 |
|---|---|---|
| /ai | AI 助手（能力点注册表+运行+Ask-first 提示） | /ai/capabilities + :cap/run |
| /report | 报表中心（预置四表+水印 CSV 导出+AI 三句话解读） | /reports/* + AI-11 |
| /kb/dept/:id | BV-01 部门看板六区（KPI 状态灯/项目卡/红灯/负荷/单据动态） | FR-M10-025 |
| /kb/project/:id | BV-03 项目监管五区（九阶段时间线/关键节点卡/团队/财务） | FR-M10-027 |

### 浏览器实测
- 页面内直验：AI-11 解读返回三句话（含 BR-02 提示）；经营底稿 5 指标键齐全
- 主题切换器在头部渲染（暗/亮 + 12 色 ColorPicker）

## 遗留（R3/后续批次）
- [ ] 移动端 H5 底部 Tab（07 册 §1.1）/ PDA 扫码壳
- [ ] 看板拖拽跨区/点位销项页/售后档案页（API 已就绪）
- [ ] FR-M00-012 角色化操作手册
