# SPRINT S3 · BOM + 采购库房（M05 + M06 S3 集）

> 切片 S3（06 册 §4.3）｜分支 protech-v2｜2026-11-01
> DoD：扫码入库→T+ 队列演示可跑；Vitest 65 全绿（+S3 9 条）；tsc 零错误。

## 交付内容

### M05 BOM（S3 = A 类 + 齐套 + ECN + 13 规则）
| FR | 实现 | 测试 |
|---|---|---|
| FR-M05-001/002 | BOM 建档（BOM{YYYY}-{NNNN}）+版本 draft/published/void；历史只读 | 2 |
| FR-M05-004 | 13 条内容校验规则 lint（零数量/一码多物/同码多单位/规格冲突/重复行/一物多码…），命中审计 | 1 |
| FR-M05-008/**BR-05** | 已发布 BOM 加行→422 blockCode=BR-05；ECN 处置方案必填；审批→version+1 | 3 |
| FR-M05-005 | 齐套检查：pass/short；缺料→自动请购草稿（挂项目码）+③板红灯+通知 PM；开工卡点（kitStatus≠pass 不许开工） | 2 |

### M06 采购库房（S3 集）
| FR | 实现 | 测试 |
|---|---|---|
| FR-M06-003 | 采购单 PO{YYYY}-{NNNN}+供应商下拉+承诺日（准交率基准） | 1 |
| FR-M06-004/**源头单据** | 入库必须挂到货单（采购单行匹配）；行外物料拒；超待收拒 | 3 |
| FR-M06-005/**BR-03** | 出库发货必须挂项目码 | 1 |
| FR-M06-021 | 映射表外旧码扫码拦截+audit dq_unmapped_code；映射内旧码自动换主码 | 2 |
| 幂等（11 册 §5.2） | idempotency_key 唯一约束；重放返回 duplicated（断网重传去重） | 1 |
| FR-M06-007 | T+ 推送队列：pending→(worker)pushed→(财务)confirmed；可导入行集导出 | 2 |
| FR-M06-006/016 | 库存查询（物料/法人/仓库）+安全库存告警；legal_entity 贯穿 | 契约 |
| FR-M06-019/020 | 每日快照（内部端点）；流水查询 | 1 |
| FIFO | 出库按 updatedAt 先进先出扣减 | 1 |

### Web
- /supply/stock-move 扫码作业页（07 册 3.4 四步流）：选单据→扫码（旧码自动换主码）→数量→提交；
  幂等键=方向+单据+物料+批次+数量；BR-03/映射表外旧码 BlockCodeAlert；流水表带 T+ 队列状态

## worker 新增任务
- tplus-push（pending→pushed，随 30m 扫描节奏）；inventory-snapshot（23:30 日终快照）

## 修复记录
- stockMove 到货单不存在→404 改 422（无单据不可入库属业务拦截）
- inventoryList 嵌套 relation 过滤（material:{...}）→ 先查物料 id 再 in 过滤（无外键 schema 纪律）
- 测试幂等键首尾空格不一致导致重放变超额——断言前统一键

## 遗留
- [ ] FR-M06-008 货位/三色管理（R2）、FR-M06-009/010 盘点闭环（R2）、FR-M06-011/012 呆滞（R2）
- [ ] FR-M05-006/007 B/C 类 BOM（R2）；BOM Excel 导入随 S5 导入引擎
- [ ] 补货分级审批 FR-M06-013/014（S6）；BOM 页面前端（S3 深化，扫码页已交付）
