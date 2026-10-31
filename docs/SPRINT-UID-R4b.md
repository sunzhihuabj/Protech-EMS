# SPRINT UID-R4b · 第四批（P5 详情/纪要页/模板批量/验收脚本化）

> 日期：2026-10-31 ｜ 状态：✅ 完成（build 绿 + 验收脚本 PASS）

## 一、交付

| # | 件 | 内容 | UID 依据 |
|---|---|---|---|
| 1 | **合同详情 P5** `/ct/contract/:id` | 七区固定：头部状态条 56（mono 单据号+状态 Tag）/操作区 48（状态只读判定 §2.3(b)+BR-02 阻断条）/主体信息（分组 descriptions，金额 null→—）/关联单据（未创建显示"未创建"禁假数据）/附件（BR-07 只读水印件）/流转时间轴 | UID-03 §2.3 |
| 2 | **纪要页** `/kb/meeting/:id` | FR-KB-11：参会人/红灯总数/闭环/未闭环过堂 + 逐卡结论表（三问内容）+ 打印按钮（自动亮色）；数据源为会议模式逐卡记录（生产落 kb_meeting） | UID-03 §2.2 |
| 3 | **模板页批量** | 13 条路由挂 TemplateStub（P4 骨架：路由/菜单已登记+空态带出口）——/workbench/todo、/workbench/my-task、/scm/supplier、/scm/po、/qms/inspection、/qms/8d、/eam/asset、/kb/my、/kb/metric、/bid/source、/bid/rule、/sys/code、/sys/import | UID-05 模板档、验收 1 |
| 4 | **验收脚本化** `scripts/uid-acceptance.py` | 验收 1（44 条组件路由域前缀 0 违例；redirect/内链豁免）+ 验收 8（对比度 120 项离线计算全过；--line 豁免按 UID-01 §一.4）+ 验收 13（PtLight 可访问名机械断言 0 缺失） | UID-05 §五 |

## 二、验证
| 项 | 结果 |
|---|---|
| vite build | ✓ |
| uid-acceptance.py | **PASS**（120 项对比度 + 44 路由 + 灯断言） |
| 路由总数 | 组件路由 44 + redirect 17 + mode 变体（UID-05 131 条口径中已实现+骨架占位全覆盖） |

## 三、与 RPD 对应
- FR-CT-19~26 呈现（P5 七区）；FR-KB-11 纪要骨架；UID-05 验收 1/8/13 脚本化
- P5 只读判定（§2.3(b)）：REVIEWED/FILED/SIGNED → 顶部条 + 操作收敛，金额 null → —

## 四、遗留
- 会议纪要服务端落库（kb_meeting 表已建，FR-KB-11 持久化随 job 批次）
- P5 详情页关联单据/审批记录接真实数据（审批实例服务随流程批次）
