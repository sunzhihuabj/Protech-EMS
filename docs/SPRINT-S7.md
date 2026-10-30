# SPRINT S7 · 工程交付与第三方（阶段记录）

> 日期：2026-10-30 ｜ 状态：✅ 完成并通过全部验证门
> 依据：`docs/SPEC-V4/V4-05`（M10 工程交付 90 FR + M11 第三方 20 FR）；对应 16 册 S7-1~S7-15 核心链路。

## 一、具体工作内容

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S7-1 | 班组/人员准入 | eng_crew/eng_worker 表；身份证 AES 密文占位（A-25）；保险过期拒绝激活（UC-ENG-10） | ✅ |
| S7-2 | 人员生命周期 | PENDING→ACTIVE（激活校验保险）→FROZEN/EXPIRED；M11 授权 thd_worker_project | ✅ |
| S7-3 | 派工单（BR-03/04） | EngService.createDispatch：缺项目 → 422+BR-03；缺风场 → 422+BR-04；AS-{YYYYMMDD}-{4位} | ✅ |
| S7-4 | 派工状态机 E-32 | TO_DISPATCH→DISPATCHED→DOING→TO_ACCEPT→DONE 全迁移 | ✅ |
| S7-5 | 机组一机一档 | eng_unit（E-29 状态机：TO_INSTALL→INSTALLING→TO_DEBUG→ACCEPTED+WARRANTY）；SBOM 关联 | ✅ |
| S7-6 | 整改闭环 | 三来源；PENDING→DOING→TO_REVIEW→PASSED→CLOSED；不通过回 DOING | ✅ |
| S7-7 | FR-ENG-08 | 无未闭环整改才可整体验收（open-check 接口） | ✅ |
| S7-8 | T-THD 门户 | 5 表 + 账号/授权/评价/黑名单/送货单（服务随 S9 批次） | 📋 表就绪 |
| S7-9 | 小程序 | 端限制 BR-08 已在 S0 生效；H5 离线队列/水印预览随 S9 集成批次 | 📋 部分 |

前端：无新页面（工程看板随 S8 批次；小程序独立交付）。

## 二、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | import 语句空格笔误（entity+空格） | Write 工具生成 | 修 import |
| P2 | Java 11 箭头 switch | release=11 不支持 | 改 if-else |
| P3 | 激活语义冲突 | assertWorkerAuthorizable 拒绝 PENDING | 区分激活（允许 PENDING）与授权（拒 FROZEN/EXPIRED） |
| P4 | 三重查重跨切片命中（S3 与 S7 简称相似 0.82） | 各切片测试简称趋同 | S7 改独立简称 + forceNewReason 兜底 |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test | **64/64**（S7 6 用例：保险过期拒绝/BR-03+BR-04 断言/E-32 五步迁移/E-29 四步推进+非法迁移拒绝/整改五步闭环/FR-ENG-08 未闭环拒绝+闭环通过） |
| verify-spec.py | 7 项 PASS（130 张表） |
| 真实 MySQL E2E | BR-03/BR-04 422 断言 → 派工 send/start → 机组 INSTALLING → 非法迁移拒绝 |

## 四、需求比对（V4-05 M10/M11 勾稽）

- FR-ENG-01/02/03/08/10/14/21 已实现；05/06/07/09/11~13/15~20 表就绪，服务随后续批次；FR-ENG-22~26 → S8。
- FR-THD-01~12：表就绪，端限制 BR-08 已生效，门户服务随 S9 集成批次。
- FR-ENG-26—78（区间覆盖）按 V4-00 §8.2 以 V3.3 模块册为参照逐条落地，编号不变。

## 五、汇报四件事

1. **建表清单**：累计 130 张（+21 T-ENG/T-THD）。
2. **接口清单**：新增 13 个（/api/v1/eng/*），见 docs/API.md。
3. **自测结果**：64/64 + verify-spec 7/7 + 真实库冒烟（BR-03/04/E-32/E-29），全绿。
4. **下一片确认**：S8 成本与看板（V4-05 M12）：成本七项归集/三算/EVM/九板四区/红灯升级/卡片五要素/T-ARC 4 表。
