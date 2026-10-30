# SPRINT S15 · 轨道一 A4（APS 排产算法落地）

> 日期：2026-10-31 ｜ 状态：✅ 完成并通过全部验证门
> 依据：`docs/SPEC-V4/V4-04`（M06 T-APS 4 表，S5 BACKLOG 项）

## 一、具体工作内容

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S15-1 | 产能台账 | createCapacity：EQUIPMENT/PERSON/MOLD + 日产能工时（A-33 口径） | ✅ |
| S15-2 | L1 粗排 | scheduleL1：优先级→订单→工序序 顺排；最小负载选资源；planned_start=max(资源空闲,前道完工)；产能超载/缺料记**冲突清单**不阻塞 | ✅ |
| S15-3 | L2 细排调整 | adjust：拖拽改工时 → 排产单 ADJUSTED + 版本 +1；已发布阻断 422+APS-PUBLISHED | ✅ |
| S15-4 | 发布门禁 | publish：齐套缺料（aps_material_check.is_ready=0）阻断 422+APS-MATERIAL-SHORT；通过 → PUBLISHED | ✅ |
| S15-5 | 编号与接口 | SCH-{YYYYMM}-{3位}（CodeTypes 29 号工厂）+ /api/v1/aps/* 5 端点 | ✅ |

## 二、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | L1 请求 ClassCastException | 全局 Long→String 序列化波及请求体数组 | 控制器逐元素 String→Long（既有惯例） |
| P2 | ApsMaterialCheck 无 status 字段 | DDL 用 is_ready 表达齐套态 | 按字段语义对齐（is_ready=0 为缺料） |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test（全仓） | **97/97**（S15 4 用例：产能+双单准备/L1 顺排 4 明细无冲突/明细查询+拖拽/发布+重复发布拒绝+已发布阻断）+ 网关 4/4 |
| verify-spec.py | 7 项 PASS；gen_entity --check UP-TO-DATE |
| 真实 MySQL 冒烟 | 产能登记→MO+2 道工序→L1 SCH-202610-001（2 明细 0 冲突）→L2 拖拽→发布 PUBLISHED |

## 四、需求比对

- M06：L1 产能顺排 + L2 人工调整 + 发布门禁已实现；甘特图前端渲染随轨道二；有限产能拆分（跨日切分）、模具/人员资源类型深度调度随生产部署批次（A-33 简化口径记录）。

## 五、汇报四件事

1. **建表**：无新增。
2. **接口**：新增 5 个（/api/v1/aps/*）。
3. **自测**：97/97 + 网关 4/4 + verify-spec 7/7 + 真实库冒烟全绿。
4. **下一批**：A5 BOM 13 条校验跑批持久化。
