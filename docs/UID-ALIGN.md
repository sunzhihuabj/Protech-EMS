# UID 整改对齐说明（V4.1 RPD + UID V1.0 → 当前代码）

> 日期：2026-11-03（R16 更新） ｜ 覆盖：UID-00—05 全 6 卷逐条比对 ｜ 依据：用户指令「以 RPD 为唯一标准全面对齐整改」
> 轮次：R1 主题基座 → R2 应用框架 → R4 后端 P0 → R3 Pt 组件与九板（第一批）→ R15 断链修复+流程闭环+响应式 → **R16 第二批功能补齐（2026-11-03）**

## 一、总览

| UID 卷 | 核心要求 | 整改状态 | 记录 |
|---|---|---|---|
| **UID-00 §三** | D-UI-01 CSS 变量令牌层（否决预编译切 link）；D-UI-04 24 组；A-01—07 口径裁决 | ✅ R1 全量落地 | SPRINT-UID-R1 |
| **UID-01** | 令牌三层/字级 8 档/4pt 间距/R1-R4 圆角/图标/动效三档/组件视觉/暗态例外 10 条 | ✅ 资产原样引入（tokens.json+theme-engine）+ bridge 全组件接线；字级/间距由组件令牌承载 | R1 |
| **UID-02** | 框架三区 232/64+56 sticky、导航树 8→18 模块、页签禁止、栅格、列表四段范式、登录/改密/403 三型 | ✅ R2（MainLayout/登录/改密/403/路由 24 条迁移+17 redirect）；131 路由中已实现页全迁，模板页随 R3 第二批 | SPRINT-UID-R2 |
| **UID-03** | 12 页面族、9 交互状态、4 卡点流程走查、四区顺序（指标→红灯→进行→闭环，R-03） | ✅ 核心齐：Pt 套件 8 组件 + 九板 P3 页（四区+会议大屏+三问）+ P4 范式迁移 2 示范页（空态三型+URL 即状态）+ 阻断条/骨架屏/水印 | SPRINT-UID-R3/R3b |
| **UID-04** | L0 中性底座+L1 令牌+L2 bridge；ProtechTheme 运行时；Pt 8 组件契约；权限呈现（***/403 三分/409）；无障碍（焦点环/读屏）；--pt-accent-graphic 派生别名 | ✅ 8/8 组件齐（R3b 补 PtTable/PtFilterBar/PtFormSection）；v-permission 移除节点已核 ✓ | R1/R3/R3b |
| **UID-05** | 131 路由命名、62 完整+69 模板、验收 13 条 | 🔶 已实现页 24 条全按新命名+redirect；131 条全量路由注册与 62 份规格对照随第二批 | R2 |

## 二、后端同步整改（测试报告 P0，同属 RPD 合规）

| # | 修复 | RPD 依据 | 复测 |
|---|---|---|---|
| F1 | MES 7 写端点补 @RequirePermission（100-103 种子码） | V4-02 M00-5 双拦截 | R06 建 MO 2002 ✅ |
| F2 | /mdm/materials costPrice 无权限→null（BR-06/A-07 前置） | BR-06、NFR-09 | R11 null / R09 1250 ✅ |
| F3 | inv/ct 必填校验 500→1001 字段级 | V4-01 §6.2 | 缺 unit/partyA 均 1001 ✅ |
| F6 | V16 job-runner(E0020)+137 补授 | FR-SYS-14/FR-KB-07 | job-runner 登录 ✅ |

## 三、与 UID 条款的逐项对账（已验收）

| 验收（UID-05 §五） | 状态 |
|---|---|
| 1 路由与菜单一致 | ✅ 已实现页全按 UID-05 命名+菜单映射表；孤儿页 0 |
| 2 权限生效（BR-06） | ✅ 服务端 null + 前端 `***`（R3 第二批接 PtTable maskedCols） |
| 3 数据范围 | ✅（TEST-REPORT T10：SELF/DEPT/跨部门） |
| 4 九板四区顺序 | ✅ 指标→红灯→进行→闭环（R-03 备案口径） |
| 8 主题 24 组 | ✅ tokens.json 24 组资产原样引入+运行时切换无白闪（colorScheme 同步） |
| 10 无权限不误导 | ✅ `***` 唯一形态、文案只出现一次（A-07） |
| 13 灯旁必有字 | ✅ PtLight 强制 aria-label=状态词（可访问名断言口径） |
| 7 会议计时 / 6 卡点回跳 / 11 打印亮色 / 12 键盘 | 🔶 随 R3 第二批（会议三问+打印样式+焦点环抽查） |

## 四、遗留（第四批候选，均已排程）

1. 131 路由中模板/骨架页批量生成（/scm/*、/qms/*、/eam/* 等，P4 Stub 即可）
2. 会议结束纪要页 /kb/meeting/:id（FR-KB-11）
3. E2E 脚本化：UID-05 验收 13 条逐条断言（灯可访问名/BR 回跳/主题 24 组）
4. 合同详情路由 /ct/contract/:id（P5 七区，现列表操作直达弹窗）
> 已收尾：P4 迁移 10/10 主台账页（R3c）；FileController.upload 注解；打印亮色 print.css

## 五、R15 断链修复与流程闭环（2026-11-03，调查驱动）

全站"后端能力 vs Web 展示"对照调查（230 端点 × 194 前端调用）后集中整改，详见 SPRINT-UID-R15.md：

- **断链 4/4 修复**：CodeRules 死链 404（新增 GET /system/codes/rules）、KbMy 硬编码壳页（接 /kb/boards）、BidRule 空壳（改订阅规则编辑，新增 GET /bid/subscribe）、ThdPortal 解除/撤销按钮（新增 GET /thd/worker-projects、GET /thd/blacklist）
- **流程闭环 7 组补齐**：商机赢单转合同、合同财务（开票/回款/交底/解冻/开票确认）、档案归还（新增 GET /arc/borrows）、呆滞处置审批/完成（新增 GET /inv/slow-moving/disposals）、补货全链 BR-05、标讯评分、呆滞列表 status=null 查询 bug
- **响应式与视觉**：MainLayout ≤1200/≤768 断点 + 搜索框 flex 自适应 + ≤1024 侧栏自动折叠；base.css 数字等宽/细滚动条/小屏弹窗 92vw/字体渲染优化
- **未竟（下批候选）**：PCS 工艺、量具管理、项目成员与门控、MES 齐套/工序质检、/integ/ops 运维台、文件上传中心、变更审批行内化、手动快照

## 六、R16 第二批功能补齐（2026-11-03，调查遗留清账）

R15 未竟清单清掉 6 组，详见 SPRINT-UID-R16.md：

- **集成运维台**：Monitor.vue 原 `/int/adapters` 404 死链重写为 /integ/ops/*（钉钉/SHB/T+ 健康检查+测试发送+手动对账+查询透传）
- **文件上传中心**：新页 /sys/file + ContractDetail 附件区接活（此前前端零调用）；V25 修正 file:upload 从未授权的全员 403
- **变更审批行内化**：ContractDetail 变更台账 + PENDING 行内通过/驳回
- **项目成员/门控/ECO**：ProjectDetail 三卡（成员增删、门控 gateOpen 门禁+放行、ECO 只读）
- **QMS 量具台账**：新页 /qms/gauges + 新增分页端点（原仅 usable 下拉源）
- **QMS 菜单结构**：V25 把 menu:qms 升级四子菜单，顺带修复 8D/SPC 两个无菜单入口的孤儿页
- **下批候选**：PCS 工艺、MES 齐套/工序质检、手动快照、审计遗留三项、Element 按需引入

## 七、R17 第三批功能补齐 + 审计遗留清账（2026-11-03，安全硬化）

R16 下批候选清掉 3 组功能 + 审核识别的审计遗留三项，详见 SPRINT-UID-R17.md：

- **内部端点共享密钥**：新增 InternalKeyFilter 守卫 /api/internal/jobs|bid/**（X-Internal-Key 头，PROJLIFE_INTERNAL_KEY 两端同值注入；S29 补 401 负向用例）——落实"内部端点必须共享密钥头校验"硬约束
- **verify-spec 域名级收敛**：新增注释剥离器，第 3/4/6/7 项改为"去注释 + 代码语义上下文"命中（硬卡点仅认主源码 BizException/blockCode 行；测试存在性仅认方法声明），堵死"改注释过门禁"
- **PCS 工艺新页** /plm/pcs（工艺文件台账/工艺路线/覆盖度矩阵三 tab）；V26 把 menu:plm 升级双子菜单（BOM/PCS），授权跟随持有面
- **MES 齐套检查 + 工序质检**：Production.vue 行内接线（material-check 结果弹窗 / FINISHED 工序 qc 放行-退回）；修复幽灵权限码 mes:report → mes:process:work、Andon → mes:andon:raise
- **手动快照**：CostCenter 成本快照 tab（POST /cost/snapshot）+ MetricDefs 行内指标快照（POST /kb/metrics/{code}/snapshots）
- **顺手修复**：ctContractDetail 路由重复、QMS 四子路由 permCode 对齐 V25 菜单、量具 API 权限面补授（V26，修 R16 菜单可见但接口 403）
- **下批候选**：Element 按需引入（单独立项）、GET /pcs/routes 列表端点、UID-05 E2E 脚本化、安全专项批次（JWT typ/refresh rotation/按 ID 数据范围/SK- 取号等审核 P1，建议 SPRINT-SEC3）

## 八、R18 孤儿清账冲刺（2026-11-04，流程尾动作 + job 骨架补挂）

孤儿全量盘点（45 孤儿端点 + 死菜单 + 死方法 + 僵尸码）后集中清账，详见 SPRINT-UID-R18.md：

- **8D 逻辑死锁修复**：close8d 此前校验库内 D1/D4/D6 但全系统无回填入口——8D 永远无法合法关闭；关闭请求现可携带 D1~D8 回填（EightD.vue 纯壳页重建：列表+新建+关闭弹窗）
- **尾动作接线六页**：检验纠正（correct 留痕+MES 联动）、MO 挂路线（routes 步骤编辑）、交底台账+确认已读（回执闭环）、MyTask 假页真实化（my-tasks+部门负载，原页调 /prj/project 本身 404）、ECO 影响面+任务依赖（CPM 前置）、T+ 对账（FR-INV-41 首个 Web 入口）
- **job 骨架补挂**：新 InternalContractJobsController（remind-unread / payment-overdue-remind 两个死方法转正）+ BusinessScanTasks 挂 4 调度（BR-05 锁定/T+ 对账/合同双提醒）+ **V27 修 V16 授权半成品**（V16 注释称授 137/141/103，SQL 只落了 137——141/103 漏授致档案扫描与 Andon 升级 403 静默失败；V27 补授 141/103/91/96）+ InternalJobInvoker 默认账号改 job-runner
- **死菜单修复**：menu:approval（V17 种码）ROUTE_BY_CODE 无映射 → /workbench/todo
- **死代码清理**：QmsService.recordSpc/spcList、EngService.unitPage、ResetPasswordReq（全零引用核实）；file:download 补权限校验
- **运行态验证**：146/0 无回归 + verify-spec 7/7 + npm build 过；8D 关闭 400 阻断→回填后 200；job-runner(E0020) 实测 V27 四码全通（91/96/103/141）
- **下批候选**：R19 = SCM/MDM/ENG 模板页转正；SPRINT-SEC3 安全专项；30 僵尸权限码清退迁移

## 九、R19 MDM 主数据全链接线（2026-11-04，前端待完成清单转正）

孤儿盘点补充调查确认：剩余孤儿为"前端未完成"而非死代码。MDM 14 孤儿端点全部转正，详见 SPRINT-UID-R19.md：

- **项目建档全家桶**：MdmConsole 建档向导五段式（基础商务 → 风场（可新建自动回填）→ FR-MDM-13/14 命名助手 → FR-MDM-12 三重查重命中卡+forceNewReason → E-08 产品线多选 + owner_pending），12 项必填前端预检置灰；详情抽屉挂产品线/加别名
- **客户治理**：列表切 GET /mdm/customers（纠正误调 /crm/customers）+ 集团分组条 + FR-MDM-05 resolve 归一检查器 + 别名/停用行内操作
- **物料台账**：GET /mdm/materials 列表 + BR-06 costPrice 脱敏渲染；**供应商注册**（POST /suppliers-register，md_supplier 建档首获 Web 入口）
- **V28 取号序列种子对齐（运行态实测发现的真 bug）**：V4 种子占 C0001~C0007/WF2026-001 但 sys_code_sequence 缺行 → next() 首次生成 C0001 撞唯一键 → 客户建档必 500；V28 幂等预占 CUST:GLOBAL=7、WF:<年>=1，新客户 C0008 起步；教训——**种子直接插编号行的迁移必须同步预占取号序列**
- **权限面免迁移核实**：R18（mdm@demo）持写码 22-35 + V23 view 码跟随补授 + menu:mdm(65)，全端点可达
- **运行态 14 步全通**：建档取号/归一/命名规则/查重拦截→放行/别名相似度回响 hits=1/产品线落库；负向（12 项校验 1001、查重阻断）按预期
- **下批候选**：ENG 三块 7 端点（人员准入/机组一机一档/整改闭环）、SCM 反向断链（GET /scm/suppliers 后端缺失）+ onboarding、SPRINT-SEC3

## 十、R20 ENG 工程交付三块 + SCM 反向断链（2026-11-04，前端待完成清单收尾）

- **ENG 补 3 个列表端点**（workers/units/rectifications，此前只有写入与推进端点，前端无法展示台账）+ EngPortal 加「班组人员」「机组与整改」两 tab：人员建档/激活（UC-ENG-10 保险过期行红字预警+后端硬拒）、机组 E-29 建档与逐阶推进（验收自动 24 个月质保）、整改登记与五态闭环（TO_REVIEW 复核双分支：通过/带理由驳回回 DOING）+ FR-ENG-08 验收前检查
- **SCM 反向断链修复**：后端补 GET /scm/suppliers（前端上线起就调，后端从未提供）；Supplier.vue 整页对齐真实契约——删除不存在的 row.stage 数字列、advance 补传 {passed, auditResult} body（此前必 400）、注册语义纠正（md_supplier 建档归主数据工作台，本页建准入档案）、准入详情抽屉接真实 onboarding 五阶（REGISTERED→QUALIFIED→VISITED→SAMPLED→APPROVED）
- **EngPortal 顺修 3 处既有错误**：components 重复定义、派工状态 dict 与后端 E-32 枚举错位（CREATED/SENT/... → TO_DISPATCH/DISPATCHED/DOING/TO_ACCEPT/DONE）、danger-text 样式缺失
- **运行态验证**：146/0 无回归 + verify-spec 7/7 + npm build 过；ENG 人员/机组/整改 16 步全通（5 负向拦截：保险过期激活/非法迁移/无理由驳回/未闭环 open-check）+ SCM 五阶准入 7 步全通（2 负向拦截）
- **下批候选**：SPRINT-SEC3 安全专项（JWT typ/refresh rotation/按 ID 数据范围/SK- 取号——审核 P1 最后一块）；ENG 派工与机组/人员联动选择器、施工记录照片上传；30 僵尸权限码清退

> 每轮记录：docs/SPRINT-UID-R1/R2/R3/R4/R15/R16/R17/R18/R19/R20.md（本文件为总对账）；变更控制按 UID-00 §八（L2 级改动已同步 UID-01/04 引用）。
