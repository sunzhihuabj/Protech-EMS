# SPRINT S16 · 轨道一 A5（BOM 13 条校验跑批持久化）

> 日期：2026-10-31 ｜ 状态：✅ 完成并通过全部验证门
> 依据：`docs/SPEC-V4` FR-PLM-13（06 册 §5.1 BOM 数据质量 13 条）+ S3 BACKLOG 项

## 一、具体工作内容

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S16-1 | 跑批入口 | BomService.runQcBatch：逐 BOM 全量 13 条校验 → 汇总 total/pass/fail + 明细清单 | ✅ |
| S16-2 | 结果持久化 | 快照（pass/issueCount/issues JSON）落 **bom_header.qc_result**（A-34：不在 163 表登记清单外建表） | ✅ |
| S16-3 | 快照回查 | GET /bom/{id}/qc-result（含 H2 JSON 包裹解包，后随 A-30 改 VARCHAR 根治） | ✅ |
| S16-4 | 端点 | POST /bom/qc-batch + GET /bom/{id}/qc-result（bom:release 权限） | ✅ |
| S16-5 | 防线口径 | 全量 ERROR 类规则已在 addLine 写入侧前置拦截（BR-PLM-04/FR-PLM-14/15/12），跑批对存量数据兜底并输出 WARN/INFO 明细 | ✅ |

## 二、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | qc_result 回查 9001 | H2 JSON 列 getString 包裹数组（A-30 同源） | 列改 VARCHAR(4000) 承载 JSON 文本 |
| P2 | 跑批 fail 分支无法经 API 构造 | ERROR 规则全部在写入侧拦截（设计使然） | A-34 记录口径；测试用规则 13 INFO 验证快照明细 |
| P3 | 插入位置导致 @Transactional 双注解 | 文本替换锚点落在 release 注解下 | 注解归位（runQcBatch/release 各自持有） |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test（全仓） | **100/100**（S16 3 用例：建料建表/双 BOM 跑批汇总/快照回查 issueCount≥1）+ 网关 4/4 |
| verify-spec.py | 7 项 PASS（无登记清单外新表）；gen_entity --check UP-TO-DATE |
| 真实 MySQL 冒烟 | 建料→建 BOM→加行→qc-batch（pass=1 issues=1）→qc-result 回查 True/1 |

## 四、需求比对

- FR-PLM-13：13 条校验实时 + 跑批双通道齐备；bom_qc_result 独立表按 A-34 落列承载；质量趋势看板随轨道二前端。

## 五、汇报四件事

1. **建表**：无新增（V6 bom_header 加 qc_result 列）。
2. **接口**：新增 2 个（/api/v1/plm/bom/qc-batch、/{id}/qc-result）。
3. **自测**：100/100 + 网关 4/4 + verify-spec 7/7 + 真实库冒烟全绿。
4. **下一批**：A6 KB 指标定义/下钻/快照（S8 BACKLOG 末项），完成后轨道一收官。
