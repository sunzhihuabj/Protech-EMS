# SPRINT S25 · 轨道二深化（主数据台账 + 下拉端点修正）

> 日期：2026-10-31 ｜ 状态：✅ 完成

## 一、交付
1. mdm/MdmConsole.vue：客户主体（新建/列表）/项目档案/物料主数据三页签（单位 E-20 受控 8 种下拉）。
2. 修正 Quotations.vue 客户下拉端点（/crm/customers 不存在 → /mdm/customers， Opportunities.vue 既有正确写法对齐）。
3. 侧边栏 menu:mdm 从占位指向 /mdm/console。
## 二、验证
- vite build ✓；MDM 端点路径逐一核对（GET/POST /customers /materials /projects）。
