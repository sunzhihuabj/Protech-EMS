# SPRINT S11 · 收尾批（表就绪服务落地：盘点 + 差异 + 档案管理）

> 日期：2026-10-31 ｜ 状态：✅ 完成并通过全部验证门
> 依据：`docs/SPEC-V4/V4-04`（M07 T-INV-06/07 盘点/差异）+ `V4-02`（M13 T-ARC 档案管理）

## 一、具体工作内容

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S11-1 | 盘点单全链路 | InventoryCountService：A/B/C/FULL 四类 + CT 编号 + CREATED→COUNTING→DIFF_PROCESS 状态机 | ✅ |
| S11-2 | 实盘录入 | entry：diff=actual−book（BR-06 仓储不见价格，diff_amount 财务回填） | ✅ |
| S11-3 | 无归因不调账 | review：差异行必须 E-37 六类归因（SCRAP/LOSS/OVER/PROCESS/SUPPLIER/OTHER），缺一 422+INV-NO-CAUSE | ✅ |
| S11-4 | 差异分级审批 | diff 单 DF 编号 + L1(<1万)/L2(<10万)/L3 动态权限校验（A-31）+ 闭环 close_days | ✅ |
| S11-5 | 档案管理 | ArchiveService：建档 PRT-{YYYY}-{门类}-{期限}-{4位}/借阅 JY-/销毁 XH-/移交 YJ-（CodeTypes 25-27 号工厂） | ✅ |
| S11-6 | 在借守卫 | 重复借出 409；在借不得销毁 422+ARC-BORROWED | ✅ |
| S11-7 | 销毁鉴定 | 鉴定意见必填 422+ARC-NO-APPRAISAL（04 册档案鉴定）；销毁后档案 DESTROYED | ✅ |
| S11-8 | 移交确认 | PENDING→DONE，档案归入接收部门 | ✅ |
| S11-9 | 逾期扫描 | overdueScan：OUT 且应还日已过 → OVERDUE（job 入口） | ✅ |

## 二、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | 借阅/销毁 insert 9001（approver_id/approved_by NOT NULL） | 测试未传审批人 | 审批人缺省落当前操作人（流程审批随后续批次） |
| P2 | 盘点行集传递 | 生成实体无 transient 行集 | create(count, lines) 双参数，控制器 ObjectMapper.convertValue 拆包 |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test（全仓） | **82/82**（S11 4 用例：盘点创建/开始/录入/无归因拦截/补归因/L1 审批/重复借出 409/归还/销毁鉴定必填/在借销毁拦截/移交确认）+ ai-gateway 4/4 |
| gen_entity --check | 142 实体 UP-TO-DATE |
| verify-spec.py | 7 项 PASS |
| 真实 MySQL 冒烟 | 盘点 CT-20261031-001→录入→差异 DF-202610-0001(L1)→审批闭环；档案 PRT-2026-HT-P30-0001→借 JY-202610-001→还→销 XH-202610-001→移交 YJ-202610-001→确认 DONE |

## 四、需求比对

- M07：FR-INV 盘点/差异/审批主链路已实现；呆滞（inv_slow_moving）/处置（inv_disposal）表就绪，服务随生产部署批次（E-38/E-39 归因与审批级随财务口径确认）。
- M13：T-ARC 借/销/移交管理闭环已实现；到期鉴定提醒 job 与到期续借随运维批次。

## 五、汇报四件事

1. **建表清单**：无新增（S11 复用 V7/V12 就绪表），累计 141 张。
2. **接口清单**：新增 10 个（/api/v1/inv/counts* 5 个 + /api/v1/arc* 7 个，见 docs/API.md）。
3. **自测结果**：82/82 + 网关 4/4 + verify-spec 7/7 + 真实库冒烟，全绿。
4. **后续**：余项 BACKLOG（呆滞/处置服务、THD portal、eng 日志、APS 排产、KB 指标大屏）+ 生产部署批次（DMZ/真实模型/钉钉），均记录于各 SPRINT 文档 §需求比对。
