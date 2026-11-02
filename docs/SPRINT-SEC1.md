# SPRINT-SEC1：审计高危项修复批次一（F4/F5/F6 + F2/F1/F6 复核）

> 日期：2026-11-03 · 分支：`feature/dev` · 输入：docs/AUDIT-REPORT.md（深度审核 P0/P1）
> 复核结论：审计报告早于 R4b/INT 批次，逐项核实现状后再动手——**F2 已修**（VersionedEntity 已带 @Version）、F1 大规模注解缺口另立批次、F6 写路径 evict 已齐全仅 TTL 过长。

## 一、修复内容

### F4【P1 并发竞态】库存可用量 TOCTOU → 原子条件扣减
- `InvStockCacheMapper` 新增 `deductIfAvailable`（`UPDATE ... SET qty=qty-? WHERE qty_available >= ?`，返回 1/0）与 `addQty`（原子增量）；全库 qty 变更改走原子 UPDATE，根除"查-判-插"竞态（并发两笔出库不可能同时通过，库存不可能为负）。
- `InventoryService.submit()` 出库分支：先原子预扣 → 成功则跳过 refreshCache 数量调整（防双重扣减）→ 失败且有行=可用量不足 400 → 无行（冷键）回退台账推算校验（refreshCache 建行后即全原子）。
- `refreshCache(txn, preDeducted)`：有行改 `addQty` 原子增量（入库并发安全）；冷键 select-then-insert 建行（首笔窗口极小，建行后全原子）。

### F5【P1 前端】401 无 refresh 重试 → 单飞静默续期
- `request.js`：401 时先 `POST /auth/refresh`（裸 axios 单飞，并发 401 只发一次）→ 换新 token 重试原请求一次（`__retried` 防循环）→ refresh 失败才 forceLogout。表单数据不再因 30 分钟 token 过期丢失。

### F6【P1 权限缓存】写路径 evict 复核 + TTL 收紧
- 复核：RoleService/UserService/AuthService 权限相关写路径已全部调用 evict（审计报告所述"每次新增权限都遇到"实为 V17 直改 SQL 场景，文档已有 memurai-cli 处置）。
- 残余加固：`CACHE_TTL_SECONDS 1800→600`（直改 SQL 的陈旧窗口从 30min 收紧到 10min）。

## 二、复核确认项（不重复修）
| 审计项 | 现状 |
|---|---|
| F2 @Version 乐观锁 | **已修**（VersionedEntity.version 带 @Version，某批次已落） |
| F6 evict 缺失 | **写路径已齐**（本批仅收紧 TTL） |
| F1 265 端点权限注解 | 另立大批次（需逐 Controller 对齐种子权限码，建议脚本辅助） |
| F13 纪要无服务端持久化 | **已修**（SPRINT-KB1，见该文档） |

## 三、验证结果
- 库存/看板定向 E2E 25/25 全绿（S4/S9S10/S12/S27——库存流实测走原子扣减路径）
- 后端编译 + 前端 npm run build 通过
- 全量回归：全反应堆 BUILD SUCCESS

## 四、遗留（下一批候选）
- F1（265 @RequirePermission，建议静态扫描脚本辅助分 4 批）
- F3（@DataScopeRule 数据范围仅 1 处）
- F7（九板指标取数 20+ 缺口）
- F9/F10/F11/F12/F14/F15（P2 功能完善）
