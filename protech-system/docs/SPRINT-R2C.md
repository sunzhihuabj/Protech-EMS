# SPRINT R2-C · B/C 类 BOM + 点位销项（M05 深化 + FR-M07-003）

> R2 深化切片 3｜分支 protech-v2｜2026-11-02
> DoD：参数化展开与点位销项红灯联动演示；Vitest 113 全绿（+R2C 4 条）。

## 交付内容

| FR | 实现 | 测试 |
|---|---|---|
| FR-M05-006 | B 类参数模板（标准节 BOM+附件包 BOM+参数定义 perSection）→ 录入塔高自动展开：ceil(塔高/每节高)×标准节行数量放大+附件包一套 → 生成 param BOM | 1 |
| FR-M05-007 | C 类套件包（kitCode+lines）→ 工程 BOM 点位展开（机舱/塔底/塔筒/升压站 positionCode；kitRefId 引用） | 1 |
| FR-M07-003 | 点位清单从工程 BOM 生成（防重复）；逐项销项（装一个勾一个）；进度=已销/总点位；**缺件标记→⑤板红灯自动挂卡** | 2 |

## 修复记录
- AppModule 手工合并冲突（M05BomModule 重复 import + ExpandController 未注册 → 404）
- PunchItem 补 createdBy（审计字段纪律）
- 测试进度断言分母修正（套件 1 + 塔底 2 = 总 3）

## 遗留
- [ ] FR-M05-009 BOM 对比/010 Golden BOM/011 质量看板深化（下一批）
- [ ] 销项进度进 BV-03 关键节点卡（前端批次）
