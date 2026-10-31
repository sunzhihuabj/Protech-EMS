# SPRINT UID-R10 · P8 待办中心 + SCM 前端 + Stub 消减

> 日期：2026-10-31 ｜ 状态：✅ 完成（103/103 + 4 全绿 + build 绿 + 双自检 PASS）

## 一、交付

| # | 件 | 内容 | RPD 依据 |
|---|---|---|---|
| 1 | **待办中心** `/workbench/todo` | P8 族：左侧待办列表 + 右侧审批操作条（通过/驳回≥10 字/转办/加签）；ApprovalService.myPending() 驱动 | FR-SYS-14、UID-03 P8 |
| 2 | **SCM 供应商** `/scm/supplier` | PtTable 列表 + 新建弹窗 + 状态 Tag | FR-SCM-01~04 |
| 3 | **SCM PO 台账** `/scm/po` | PtTable 列表 + 新建弹窗 + 状态 Tag | FR-SCM-08~16 |
| 4 | **QMS 检验台账** `/qms/inspection` | PtTable + 类型/结果 Tag + 新建弹窗 | FR-QMS-02~07 |
| 5 | Stub 消减 | 13→10（待办/供应商/PO/检验 4 条路由从 Stub 升级为完整页） | 验收 1 |

## 二、验证
| 项 | 结果 |
|---|---|
| vite build | ✓ |
| mvn test | **103/103 + 网关 4/4** BUILD SUCCESS |
| verify-spec / uid-acceptance | PASS / PASS |

## 三、Stub 余量
10 条（/workbench/my-task、/kb/my、/kb/metric、/bid/source、/bid/rule、/sys/code、/sys/import、/sys/monitor、/qms/8d、/eam/asset）——路由/菜单已登记，字段随切片细化。
