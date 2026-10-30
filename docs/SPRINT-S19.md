# SPRINT S19 · 轨道二 W2（前端：库存中心）

> 日期：2026-10-31 ｜ 状态：✅ 完成并通过构建门

## 一、交付

| 页签 | 内容 |
|---|---|
| 库存事务 | 幂等键事务提交（T+ 权威）/可用量查询（账面/锁定/可用）/异常重试 |
| 盘点 | 新建盘点单（行集）/开始盘点/实盘录入（E-37 归因下拉）/复核生成差异单 |
| 呆滞处置 | 登记呆滞（E-38 归因）/确认/发起处置（E-39 + 预期金额，A-31 分级提示） |

## 二、验证

- vite build ✓；端点载荷与 InventoryCountController/SlowMovingController/InventoryController 对齐。

## 三、汇报

1. 页面：inv/Inventory.vue（三页签）+ 路由 /inv/center。
2. 构建：vite build 全绿。
3. 下一片：S20 工程门户 + 第三方门户。
