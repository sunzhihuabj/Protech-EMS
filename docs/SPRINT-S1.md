# SPRINT S1 · 营销与合同（阶段记录）

> 日期：2026-10-30 ｜ 状态：✅ 完成并通过全部验证门
> 范围：02 册 §4.2-4.4 + 04 册全册 + 12 册 §2；对应 16 册 S1-1~S1-14。

## 一、具体工作内容

| # | 任务 | 交付 | 状态 |
|---|---|---|---|
| S1-1 | T-MDM 12 表 + 客户/风场/项目建档 | V4 迁移（T-MDM 12 + T-CRM 4 + T-CT 7 + ai_feedback = 24 表）+ 种子（法人 PRT/TLD、7 个 demo 客户、1 风场、R18 建档专员、3 个新菜单） | ✅ |
| S1-2 | 三重查重 + 名称生成器 + 12 项校验 | NameRuleService（①全称精确 ②简称/别名 Levenshtein≥0.6 ③业主+地名）+ generateNames（标准名=业主规范名－地名去尾缀－期次，空填"—"）+ validateRequired | ✅ |
| S1-3 | 商机全流程 | OpportunityService：建档（OPP 编号）/跟进时间线/转合同/丢单（必填原因）/重激活；BR-CRM-01 不物理删除 | ✅ |
| S1-4 | 客户 360° + 漏斗 | CustomerProfileService（合同额/回款/应收聚合 + RFM 三档）+ funnelStats + 集团归并视图 | ✅ |
| S1-5 | 合同起草 + 付款条件建模 | 多期计划（比例自动算额，合计=合同额校验）；性质 XS/CG/KJ 联动业务类型过滤（前端） | ✅ |
| S1-6 | AI 风险分析 | AI-001 经网关（测试用真实 HTTP 桩网关）→ ct_contract_risk 落库 → 采纳/忽略 | ✅ |
| S1-7 | 评审 BR-01 | submitReview：无关联项目 → 422 + blockCode=BR-01 + "去建档"引导（E2E 断言） | ✅ |
| S1-8 | 用印编号 + 正本 | seal：CodeTypes.contract 取号（预览不占号 peek）+ filing_deadline=签订+3 工作日（跳周末实测 11-04 正确） | ✅ |
| S1-9 | AI 提取 + 确认归档 | AI-002 12 字段左右分栏 + 人工修正逐字段写 ai_feedback（E2E 断言 2 条）+ 网关 callId 贯通 | ✅ |
| S1-10 | 建档 26 要素 BR-02 | filing：26 要素白名单回填 + 完整度 100% 强制 + filed_at→PERFORMING + 自动解冻；开票接口惰性冻结（超期 422 + BR-02）+ job 批量任务 | ✅ |
| S1-11 | 交底 + 已阅 + 二次提醒 | dispatch 分发（站内+微信渠道适配）+ confirmRead + remindUnread（24h） | ✅ |
| S1-12 | 变更 -BCn | ContractChangeService：CTBC scope 序号 + 审批 + 金额增减回写 + 状态 CHANGED→回原状态 | ✅ |
| S1-13 | 收款计划 + 录入 + 确认 | 收款核销计划行（PENDING→PARTIAL/RECEIVED）+ 财务确认（R15 权限）+ 开票超额校验（BR-CT-07） | ✅ |
| S1-14 | E2E s1a | S1FlowTest 9 用例全链路（见验证结论） | ✅ |

前端：商机管理页（列表/登记/跟进时间线/漏斗卡/丢单/重激活）+ 合同管理页（台账/起草含付款计划/评审/用印（编号预览）/AI 风险采纳/AI 提取左右分栏确认/变更/收款录入）+ 路由与菜单权限。

## 二、技术要点与选型依据

- **代码生成器** `scripts/gen_entity.py`：24 组实体/Mapper 由 02 册字段规格生成（--check 可校验漂移），S2-S8 复用。
- **合同-项目多对多**：经 `md_project_product` 承载（02 册未设独立关联表，FR-MDM-17/18 口径，A-12）。
- **评审留痕**：02 册 T-CT 未设评审表，节点结论经 `sys_operation_log` 记录 + E-13 状态机承载，不擅自加表（A-13）。
- **contract_no 允许 NULL**：编号在用印时生成（FR-CT-17），起草阶段为空；唯一约束兼容多 NULL。
- **BR-02 双保险**：job 每日批量标记 + 开票接口惰性冻结判定（测试无需等定时器）。
- **AI 桩网关**：S1FlowTest 用 JDK HttpServer 起 /ai/v1/call 桩，走真实 HTTP 客户端链路，@DynamicPropertySource 注入端口。

## 三、问题与解决

| # | 问题 | 根因 | 解决 |
|---|---|---|---|
| P1 | record 语法编译失败 | target 11 不支持（Java 16+） | 改静态类 |
| P2 | 建合同 9001：contract_no NOT NULL | 编号用印时才生成 | V4 该列改 NULL |
| P3 | 付款计划合计校验误伤 | 控制器把"无计划"传成空列表 | null 语义修正 |
| P4 | List\<Long\> 元素实为 String | Long→String 全局序列化波及请求体 | 控制器逐元素转换 |
| P5 | -BCn 取号失败 | scope_key 末段（完整合同号）超 period VARCHAR(16) | scope 尾段改固定标识 SEQ |
| P6 | ai_feedback.call_id 非空 | 网关未回传调用 id | 网关响应带 callId 全链贯通 |
| P7 | curl 中文 400 | Windows 控制台 GBK | 冒烟载荷改 ASCII（服务端无缺陷） |
| P8 | 变更审批 403 | biz@demo 无 contract:change:approve（R05 权限） | 测试改用 boss@demo，符合权限矩阵 |

## 四、验证结论（验证门全部通过）

| 验证项 | 结果 |
|---|---|
| mvn test | **18/18**（S0 冒烟 9 + S1 E2E 9：商机编号格式/BR-01 422/三重查重 422+候选/PJ·合同号格式/26 要素完整度拦截/AI 提取 12 字段+ai_feedback 2 条/交底 2 人/BC1 编号+审批/收款核销 RECEIVED/BR-02 422→建档自动解冻→开票放行） |
| verify-spec.py | 7 项 PASS（41 张表全部登记在册；BR-01/02/07/08 已生效） |
| gen_entity --check | UP-TO-DATE |
| 前端 vite build | 成功（Contracts 20.6KB / Opportunities 懒加载分片） |
| 真实 MySQL E2E | 商机 OPP-202610-0002 → 漏斗统计 → BR-01 422 → R18 建项目 → 用印 `TLD-2026-CG-AZFWXF-0002` + filing_deadline=2026-11-04（跳周末） |
| 浏览器 GUI | biz@demo 登录见 5 菜单（新增商机/合同/主数据）；商机列表/漏斗渲染真实库数据 |

## 五、需求比对（04 册逐条勾稽）

- FR-CRM-01~09 已实现（FR-CRM-10 聚合/RFM 简化档位实现，FR-CRM-15 多头报价提示、FR-CRM-16 合并 → S8 治理切片补全，记 BACKLOG）。
- FR-CT-01~23 全覆盖；FR-CT-24（T+ 回填清单）/FR-CT-27（完整率报表）→ S4 T+ 集成与 S8 报表切片。
- FR-CT-28~30 交底已实现（24h 提醒 job 入口就绪，随 S4 对账任务一起启用）；FR-CT-31~33 变更已实现（AI-003 差异对比 → S9）；FR-CT-34~38 收款链路已实现。
- BR-01/BR-02 在 E2E 与真实环境双重断言通过；BR-CT-01~08 规则中 01/05/06/07 已实现，02（性质区分）由编码格式保证，03（legacy_no）字段已建，04（建档责任归经办）由 handler_id 承载，08（无发票回款单列预收款）→ S4。

## 六、汇报四件事

1. **建表清单**：累计 41 张（+24：T-MDM 12、T-CRM 4、T-CT 7、ai_feedback）。
2. **接口清单**：新增 27 个（mdm 13 / crm 8 / contract 20 中 S1 交付部分），见 docs/API.md。
3. **自测结果**：18/18 + verify-spec 7/7 + vite build + 真实环境 + 浏览器 GUI，全绿。
4. **下一片确认**：S2 项目与 ECO（02 §4.5-4.6、05 册）：项目模板 6 阶段、自动立项、WBS 任务树、依赖成环检测、ECO（BR-09 前置）、甘特数据结构。
