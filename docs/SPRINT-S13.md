# SPRINT S13 · 轨道一 A2（工程日常记录服务落地）

> 日期：2026-10-31 ｜ 状态：✅ 完成并通过全部验证门
> 依据：`docs/SPEC-V4/V4-05`（M10 T-ENG 日常记录四表）+ AI-009 能力点

## 一、具体工作内容

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S13-1 | 施工日志 | EngRecordService.dailyLog：BR-03 派工关联校验 + 内容 ≥5 字 + AI-009 合规检查（经 ai-gateway 客户端，**降级不阻塞**，结果/降级标记落 ai_check_result） | ✅ |
| S13-2 | 质量检查 | qualityCheck：PASS/FAIL 二值；FAIL 必填缺陷描述 422+ENG-QC-DEFECT（联动整改闭环入口） | ✅ |
| S13-3 | 现场物料 | issue（数量为正）+ returnMaterial：**累计退料不超领料** 422+ENG-RETURN-OVER；全退置 RETURNED | ✅ |
| S13-4 | 售后工单 | WO-{YYYYMMDD}-{3位} 编号 + TO_DISPATCH→RESPONDED→CLOSED 状态机；**闭环方案必填 ≥5 字** 422+ENG-WO-SOLUTION + customer_confirm=1 + cost_amount 回填 | ✅ |

## 二、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | ai_check_result 写入 MySQL 拒收 | Map.toString 非 JSON（H2 宽松老问题） | Jackson 序列化（与 S10 P4 同根因，已形成惯例） |
| P2 | S13 前置项目撞 S1/S7 测试数据（同风场同产品线 FR-MDM-16 / 三重查重 OWNER_LOCATION） | S13 用了 customerId 6 + farmId 1 + P7 + LADDER | S13 改独立组合 customer 2 + farmId 9/10 + P9 + TOWER（跨测试数据隔离惯例生效） |
| P3 | 测试跑全量顺序变化引发连锁 | 新增类改变类执行顺序 | 复跑三套确认隔离 |

## 三、验证结论

| 验证项 | 结果 |
|---|---|
| mvn test（全仓） | **90/90**（S13 5 用例：前置派工 BR-03/04/日志 AI 降级落库/质检 FAIL 缺陷必填/领退料守卫/售后闭环客户确认）+ 网关 4/4 |
| verify-spec.py | 7 项 PASS；gen_entity --check UP-TO-DATE |
| 真实 MySQL 冒烟 | 日志（AI 网关不可达→降级 JSON 落库不阻塞）→质检 FAIL 拦截→PASS→领 10 退 4→退 7 拦截 ENG-RETURN-OVER→WO-20261031-001 响应→闭环 CLOSED |

## 四、需求比对

- M10：施工日志/质量检查/现场物料/售后工单主链路已实现；离线补传（offline_flag 字段就绪，同步策略随小程序批次）；日志 AI 视觉审核（照片安全帽识别）随真实模型接入。
- eng_daily_log.ai_check_result 为 JSON 列读回场景仅列表展示，暂按 A-30 同款处理（实体 String 承载，MySQL 写入保证合法 JSON）。

## 五、汇报四件事

1. **建表**：无新增。
2. **接口**：新增 8 个（/api/v1/eng/records/*）。
3. **自测**：90/90 + 网关 4/4 + verify-spec 7/7 + 真实库冒烟全绿。
4. **下一批**：A3 第三方门户 THD（订单只读/送货单打印）。
