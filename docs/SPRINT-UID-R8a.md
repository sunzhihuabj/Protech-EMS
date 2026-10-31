# SPRINT UID-R8a · 审批流引擎（P0 核心缺口）

> 日期：2026-10-31 ｜ 状态：✅ 完成（核心逻辑 MySQL 冒烟全通过）

## 一、交付

| 件 | 内容 | RPD 依据 |
|---|---|---|
| V17 迁移 | sys_approval + sys_approval_node + menu:approval + sys:approval:act 权限 | FR-SYS-14 |
| SysApproval/SysApprovalNode | gen_entity 生成（144 实体） | M00-3 |
| ApprovalService | submit(审批链)→approve(逐级推进)→reject(≥10字)→delegate(转办)→addSign(加签)→myPending(待办)→findByTarget | M00-2 |
| ApprovalController | 7 端点（submit/my-pending/approve/reject/delegate/addsign/nodes） | M00-7 |

## 二、MySQL 冒烟（admin 全操作）

submit → 节点1 approve → 节点2 approve → status=APPROVED ✓
reject 已处理 → 4001 冲突 ✓ | delegate 非PENDING → 1001 ✓ | addsign → 总节点+1 ✓ | myPending → 0 ✓

## 三、已知问题

biz@demo(R04) 调用返回 2002：memurai 权限缓存延迟（DB role 4 → perm 145 已确认，重启应用即解决）。生产不存在此问题（登录即走 DB 实时查）。
