# SPRINT S8 · 成本与看板 + 档案（阶段记录）

> 日期：2026-10-31 ｜ 状态：✅ 完成并通过全部验证门
> 依据：`docs/SPEC-V4/V4-05`（M12 成本看板 62 FR）+ `V4-02`（M13 T-ARC 4 表）

## 一、具体工作内容

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S8-1 | 成本七项归集 | CostService.createItem（E-33 七项校验/CI 编号/来源单据） | ✅ |
| S8-2 | 七项合计校验 | summaryByType（按类型分组+总计） | ✅ |
| S8-3 | 超支预警 | overrunCheck（10% YELLOW / 20% RED / NO_BUDGET） | ✅ |
| S8-4 | EVM | computeEvm（SPI=EV/PV，CPI=EV/AC） | ✅ |
| S8-5 | 成本快照 | snapshot（每日 02:00 job 入口；唯一约束项目+日） | ✅ |
| S8-6 | 卡片五要素+VAGUE 拦截 | KbCardService.create（缺一不许上板+VAGUE 正则拦截 UC-KB-01） | ✅ |
| S8-7 | 红灯卡点必填 | block()（<5 字拒绝 422+SYS-KB-BLOCK-REASON，UC-KB-02） | ✅ |
| S8-8 | 红灯 3 天升级 | escalateRedCards（BLOCKED 超 3 天 → ESCALATED + Day3 升级记录，UC-KB-03） | ✅ |
| S8-9 | 三问过堂闭环 | close()（<5 字视为未闭环 UC-KB-06） | ✅ |
| S8-10 | 补录率 | backfillRate()（≤5% 口径） | ✅ |
| S8-11 | T-ARC 档案 | 4 表（archive/borrow/destroy/transfer）服务随 S9 批次 | 📋 表就绪 |

## 二、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | V12 建 kb_board 失败 | V2 已建该表 | V12 去重（V2 九板种子保留） |
| P2 | arc_archive `year` 保留字 | H2 不允许 `year` 做列名 | A-26：改名 `archive_year` |
| P3 | VAGUE 9001 | `BizException.badRequest("").getCode()` 链式 hack | 直接用 `ErrorCode.PARAM_INVALID` |
| P4 | 测试 2002 权限 | fin@demo 无 kb:card:manage | KB 测试切 biz@demo(R04)；成本切 fin@demo(R15) |
| P5 | cost_item source_type NOT NULL | 测试未传 | 补 sourceType |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test | **70/70**（S8 6 用例：成本归集+七项合计/超支预警/EVM SPI=0.8 CPI=1.4286/VAGUE 拦截/五要素缺一拒绝/红灯卡点必填/3 天升级/三问闭环） |
| verify-spec.py | 7 项 PASS（130 张表全登记） |
| 真实 MySQL E2E | 成本归集 total=200000 → EVM SPI=0.8 CPI=1.4286 → KB VAGUE/建卡/红灯/闭环（权限矩阵验证） |

## 四、需求比对（V4-05 M12 勾稽）

- FR-COST-01/02/05/07/10 已实现；FR-COST-03/04/06/08/09/11/12 表就绪，服务随 S9 成本看板展示批次。
- FR-KB-01~13 已实现（九板种子在 V2 已导入）；FR-KB-14~42 指标定义/下钻/大屏 → BACKLOG 优先。
- T-ARC 4 表就绪，档案管理服务随 S9 批次。

## 五、汇报四件事

1. **建表清单**：累计 130 张（+16 T-COST/T-KB/T-ARC，V12 不重复建 kb_board）。
2. **接口清单**：新增 10 个（/api/v1/cost/* + /api/v1/kb/*），见 docs/API.md。
3. **自测结果**：70/70 + verify-spec 7/7 + 真实库冒烟，全绿。
4. **下一片确认**：S9 AI 真实接入 + M15 集成 + S10 招标情报 + Agent。
