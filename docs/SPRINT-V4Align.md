# SPRINT V4 对齐 · 新 RPD（Protech V4.0）接入（阶段记录）

> 日期：2026-10-30 ｜ 状态：✅ 完成并通过全部验证门
> 依据：`docs/SPEC-V4/`（V4-00—V4-07 共 8 卷，唯一开发输入；V3.3 归档只读）

## 一、新 RPD 差异分析（V4-00 通读结论）

| 变化 | 对现有代码的影响 | 处置 |
|---|---|---|
| 模块编号 A—J/D## 双轨 → **M00—M19 单轨**（D-01） | 代码注释中 FR 编号不变（D-03 保留全部编号） | 仅文档映射，无代码改动 |
| 新增报价单 `crm_quotation`+`crm_quotation_line`（D-04，FR-CRM-17/18/19） | M01 需新增服务/接口 | 本片交付 |
| 商机增 source_type/source_id/source_note（F-17/D-05） | crm_opportunity 表加列 | 本片交付 |
| 枚举 E-42 → **E-52**（F-09）：标讯 6 组+Agent 3 组+看板 1 组 | 字典种子 | 本片交付 |
| 编码 18 类 → **24 类**（F-08）：QT/BID/SRC/T-AGT/CARD | CodeTypes 扩展 | 本片交付 |
| blockCode 命名空间（F-27）：BR/AGT/BID/SYS 四段 | MDM-DUP-CHECK → **SYS-DUP-CHECK** | 本片修正 |
| 表基线 150 → **163**（+2 报价 +8 T-BID +3 T-AGT） | verify-spec 登记表升级 | 本片更新 |
| 切片 S0—S9 → **S0—S10**（F-16）：S10=招标情报+Agent | 计划文档更新 | 本片更新 |
| 202 异步口径（F-19）/接口四分区（F-18）/DMZ 边界（F-30） | 部署与文档层 | 记 DECISIONS，Agent 网关 S10 落地 |
| 钉钉主、企微备（D-08）；库房账实 99%/首年 98%（D-06） | 文档口径 | 已记 DECISIONS |

## 二、本片交付

| 项 | 内容 | 对应 |
|---|---|---|
| V10 迁移 | crm_quotation/line 两表 + 商机来源三列 + E-43—E-52 枚举种子（52 组全量）+ crm:cost:view 与 bid:*/agt:* 权限登记（F-05） | V4-03 M01 §3、V4-01 §5.4 |
| QuotationService | FR-CRM-17 建档（QT 编号/版本 V1/V2 递增/金额=数量×单价×(1+税率) 服务端算/行含税合计校验）/提交/FR-CRM-18 价格基准/FR-CRM-19 定标（WON→合同草稿+回写 converted_contract_id；LOST 必填原因）/FR-CRM-20 转化率按来源分组 | V4-03 M01 §2 |
| ContractDraftBridge | crm→contract 桥接（依赖方向 crm←contract，无循环） | FR-CRM-19 R1 |
| 商机状态机 | TRACKING → QUOTING（有报价）→ WON/LOST（M01 §4） | V4-03 M01 §4 |
| blockCode | MDM-DUP-CHECK → SYS-DUP-CHECK（F-27 四段命名空间） | V4-01 §7.3 |
| verify-spec 升级 | 登记表 163 张/枚举 E-52 全量扫描全部迁移文件 | V4-01 §8.4 DoD |
| CodeTypes | +5 类 = 24 类（19 QT/20 BID/21 SRC/22 T-AGT/23 CARD） | V4-01 §5.3 |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test | **58/58**（V4Align 6 用例：QT 编号与金额/版本递增/QUOTING/价格基准/WON 转合同回写/LOST 必填原因/转化统计+枚举抽查） |
| verify-spec.py | 7 项 PASS（163 张登记/52 组枚举） |
| 真实 MySQL E2E | 报价 QT → 提交 → 定标 WON → 合同草稿 id=3 → 转化统计 33.33% |

## 四、需求比对

- V4-00 §九 12 项新增物中 9 项本片落地（报价表/3FR/来源字段/E-52 枚举/权限登记/blockCode/编号 24 类/verify-spec 升级/FR 回填）；DMZ 边界表与接口四分区记 DECISIONS（部署层，S10 Agent 网关落地时实施）；8 段固定模板用于后续模块文档。
- FR 回填：docs/API.md 已按切片标注 FR 编号（V4-00 §8.2 要求）。

## 五、汇报四件事

1. **建表清单**：累计 111 张（+3：报价主行表；ct_contract 补 quotation_id 列；商机加 3 列）。
2. **接口清单**：新增 7 个（/api/v1/crm/quotations*）。
3. **自测结果**：58/58 + verify-spec 7/7 + 真实库冒烟。
4. **下一片**：S7 工程交付与第三方（V4-05 M10/M11）。
