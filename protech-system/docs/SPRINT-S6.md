# SPRINT S6 · AI 能力点 + 报表 + 补货分级（M16 + M14 + FR-M06-013/014）

> 切片 S6（06 册 §4.3）｜分支 protech-v2｜2026-11-01
> DoD：合同要素提取演示可跑；Vitest 94 全绿（+S6 9 条）。

## 交付内容

### M16 AI 能力（R1 三点 + 治理红线）
| FR | 实现 | 测试 |
|---|---|---|
| FR-M16-001 | 能力点注册表（12 点白名单+分级 E-18）+ T-95 调用审计（谁/输入摘要/置信度/耗时） | 2 |
| FR-M16-002 | AI-01 合同要素提取：16 字段草稿+confidence（<0.8 标黄）→ Ask-first 确认入库+T-96 反馈 | 1 |
| FR-M16-003 | AI-02 查重辅助：归一+相似度预聚类+归并依据提示 | 1 |
| FR-M16-004 | AI-03 台账名称归一：候选+依据（"地名一致、业主待补"） | 1 |
| FR-M16-014 | 白名单外 403（AI-06 拒绝）；Ask-first 标记；全部调用落审计 | 契约 |
| 通道 | AI_PROVIDER=mock（确定性，恒可用）| http（网关 DeepSeek/Qwen，S7 compose 预留独立进程位） |

### FR-M06-013/014 补货分级
- tier1 ≤500 元（当天出）/tier2 500-5000/tier3 >5000；安全件 isSafetyPart 先发后签（24h 补签跟踪 signedBackAt）
- E-08 原因结构分析：原因分布 + 高频品种 TOP10

### M14 预置报表（FR-M14-001/002/006）
- 四张预置：contracts/projects/stock-moves/receipts（口径统一）；每行注水印（生成人+时间）；CSV 导出；权限 report:view/export

## 修复记录
- EXTRACT_FIELDS 与合同实际字段名对齐（amount→amountCents 等 16 字段）
- 测试 AI-03 需先造项目候选源

## 遗留
- [ ] AI-04~AI-12（R2/R3，白名单已注册 enabled=false）
- [ ] 网关独立进程（apps/ai-gateway）在 S7 compose 交付（当前 api 内联 mock 通道）
- [ ] FR-M14-003 订阅/005 经营底稿（R2）
