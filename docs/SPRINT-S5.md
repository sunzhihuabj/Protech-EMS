# SPRINT S5 · 生产与排程（阶段记录）

> 日期：2026-10-30 ｜ 状态：✅ 完成并通过全部验证门
> 范围：02 册 §4.10 + 08 册全册（C-11 工序级裁决）；对应 16 册 S5-1~S5-13 核心链路。

## 一、具体工作内容

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S5-1 | 生产订单三类型 | MesService.createProductionOrder：样机/批量/临时；MO-{YYYYMM}-{4位}；项目必填（BR-03） | ✅ |
| S5-2 | 工序路由自动生成 | generateRoutes：按步骤序列生成 WS-{MO后6位}-{2位} 工序工单（串行） | ✅ |
| S5-3 | 开工前置 | startProcess：串行前道未 DONE 拒绝（FR-MES-10） | ✅ |
| S5-4 | 完工报工 | finishProcess：合格+不良+报废=计划数校验（FR-MES-13）；报工记录（START/FINISH） | ✅ |
| S5-5 | 报检触发 | needInspection=1 → WAIT_QC（FR-MES-15） | ✅ |
| S5-6 | 检验放行 | PASS→DONE+流转下道；REJECT→LOCKED（锁定原因）+ Andon QUALITY（FR-MES-16/26） | ✅ |
| S5-7 | Andon | raiseAndon 四类报警 + escalateAndons 分级升级（15/30 分钟→2/3 级，FR-MES-30）；job 入口 | ✅ |
| S5-8 | 齐套检查 | materialCheck 接口（领料单口径）；BOM 级精确齐套在 aps_material_check 表结构就绪，算法随 S8 批次（A-21） | 📋 部分 |
| S5-9 | APS L1/L2 排产 | 表结构就绪（aps_schedule/detail/capacity）；排产算法 service 随 S8 批次交付（16 册允许 GA 二期，L1/L2 规则排产未误期——记录在 BACKLOG 优先交付） | 📋 表就绪 |

## 二、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | MesAndon 继承 BaseEntity（有 deleted） | 生成器 AUDITED 标记替换锚点不精确 | 精确锚点重替换 |
| P2 | WS 编号测试正则 \d{6} 不匹配 | MO 后 6 位含连字符（0-0001） | 按 02 册字面口径放宽为 .{6} |
| P3 | 测试工序流错序 | 测试假设首道需报检 | 按种子工序属性重排测试流（焊接/总装需检） |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test | **47/47**（S5 6 用例：MO 创建/WS 编号格式/串行前置拒绝/报工数量校验/WAIT_QC 流转/REJECT 锁定+Andon/分级升级） |
| verify-spec.py | 7 项 PASS |
| 前端 | 未新增页面（生产看板随 S8 九板批次；报工端 PC 复用通用表格） |

## 四、需求比对（08 册勾稽）

- FR-MES-01/02/03/08/10/12/13/15/16/26/28/30 已实现；FR-MES-04 齐套放行、06 在制品看板、07 完工入库（走 INV 链路）、17 工时归集 cost_item（S8 成本切片）、22~25 追溯（表就绪）→ 后续批次。
- FR-APS-01~14：表结构全部就绪；L1/L2 排产算法与甘特为 16 册允许的独立交付面 → BACKLOG 优先（A-21：GA 二期裁决不变）。

## 五、汇报四件事

1. **建表清单**：累计 95 张（+12 T-MES/T-APS）。
2. **接口清单**：新增 10 个（/api/v1/mes/*），见 docs/API.md。
3. **自测结果**：47/47 + verify-spec 7/7，全绿。
4. **下一片确认**：S6 质量与设备（02 §4.11-4.12、09 册）：检验标准 AQL/三关口/SPC/FMEA/8D/量具/设备台账/MTBF。
