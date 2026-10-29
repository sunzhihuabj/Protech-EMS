-- =====================================================================
-- ProjLife V2：基础种子（S0-15）
-- 内容：E-01~E-42 枚举字典 / 14 部门树 / 18 角色 / 权限码（org/system/mdm/营销合同/file 组）
--       / 演示用户（demo 前缀，16 册 §八 第 15 条）/ 九板 / 系统参数与审批模板
-- 口径：01 册 §组织基线（14 部门）；03 册 §2.1（18 角色 × 数据范围）
-- =====================================================================

-- ---------- 部门（14 部门 + 公司根 + 技术中心两组） ----------
INSERT INTO sys_department (id, dept_code, dept_name, parent_id, dept_type, is_chain_master, sort_no, status, created_by) VALUES
(1,  'COMPANY',  '公司本部',   NULL, 'COMPANY', 0, 0,  'ACTIVE', 'seed'),
(2,  'D01',      '总经理部',   1,    'DEPT', 0, 1,  'ACTIVE', 'seed'),
(3,  'D02',      '市场部',     1,    'DEPT', 0, 2,  'ACTIVE', 'seed'),
(4,  'D03',      '商务部',     1,    'DEPT', 1, 3,  'ACTIVE', 'seed'),
(5,  'D04',      '销售团队',   1,    'DEPT', 0, 4,  'ACTIVE', 'seed'),
(6,  'D05',      '技术中心',   1,    'DEPT', 0, 5,  'ACTIVE', 'seed'),
(7,  'D06',      '工艺部',     1,    'DEPT', 0, 6,  'ACTIVE', 'seed'),
(8,  'D07',      '生产部',     1,    'DEPT', 1, 7,  'ACTIVE', 'seed'),
(9,  'D08',      '质量安全部', 1,    'DEPT', 0, 8,  'ACTIVE', 'seed'),
(10, 'D09',      '采购部',     1,    'DEPT', 1, 9,  'ACTIVE', 'seed'),
(11, 'D10',      '计划部',     1,    'DEPT', 0, 10, 'ACTIVE', 'seed'),
(12, 'D11',      '仓储部',     1,    'DEPT', 0, 11, 'ACTIVE', 'seed'),
(13, 'D12',      '财务部',     1,    'DEPT', 0, 12, 'ACTIVE', 'seed'),
(14, 'D13',      '企管部',     1,    'DEPT', 0, 13, 'ACTIVE', 'seed'),
(15, 'D14',      '工程技术部', 1,    'DEPT', 1, 14, 'ACTIVE', 'seed'),
(16, 'D05A',     '研发设计组', 6,    'GROUP', 0, 1,  'ACTIVE', 'seed'),
(17, 'D05B',     '系统集成组', 6,    'GROUP', 0, 2,  'ACTIVE', 'seed');

-- ---------- 角色（03 册 §2.1） ----------
INSERT INTO sys_role (id, role_code, role_name, data_scope, remark, status, created_by) VALUES
(1,  'R01', '总经理',           'ALL',       '全部查看+看板主持+审批，不得创建/修改业务单据', 'ACTIVE', 'seed'),
(2,  'R02', '业务板块负责人',   'ALL',       '板块内全部查看+审批+资源调配', 'ACTIVE', 'seed'),
(3,  'R03', '系统管理员',       'ALL',       '用户/角色/字典/编码/集成/日志；无业务数据写权', 'ACTIVE', 'seed'),
(4,  'R04', '商务/销售',        'SELF',      '商机/合同/收款录入/发货申请；不得见成本', 'ACTIVE', 'seed'),
(5,  'R05', '商务主管',         'DEPT',      '部门台账+移交审批+变更审批', 'ACTIVE', 'seed'),
(6,  'R06', '技术（研发设计）', 'PROJECT',   '勘测/方案/设计/EBOM/图纸审核', 'ACTIVE', 'seed'),
(7,  'R07', '系统集成（方案）', 'PROJECT',   '项目方案/技术交底/现场支持；不得见采购价格', 'ACTIVE', 'seed'),
(8,  'R08', '工艺',             'DEPT',      '工艺文件/MBOM/工时/工序路线；不得改 EBOM', 'ACTIVE', 'seed'),
(9,  'R09', '采购',             'DEPT',      '供应商/采购/物流/退货/价格库', 'ACTIVE', 'seed'),
(10, 'R10', '计划',             'DEPT',      '齐套/请购/排产输入/交期承诺', 'ACTIVE', 'seed'),
(11, 'R11', '仓储',             'DEPT',      '扫码出入库/盘点/差异/呆滞/补货；不得见价格（BR-06）', 'ACTIVE', 'seed'),
(12, 'R12', '生产',             'DEPT',      '生产订单/工序报工/领料/Andon', 'ACTIVE', 'seed'),
(13, 'R13', '质量',             'DEPT',      '检验/SPC/FMEA/8D/量具/安全检查', 'ACTIVE', 'seed'),
(14, 'R14', '工程',             'PROJECT',   '派工/安装/记录/整改/进度/售后；不得见合同金额', 'ACTIVE', 'seed'),
(15, 'R15', '财务',             'ALL',       '开票/收款确认/成本/预算/对账；不得建主数据', 'ACTIVE', 'seed'),
(16, 'R16', '第三方-商务/财务', 'SELF',      '订单只读（无价格）/送货单打印', 'ACTIVE', 'seed'),
(17, 'R17', '第三方-施工人员',  'PROJECT',   '小程序：水印图纸/日志/照片/整改；PC 端禁止登录（BR-08）', 'ACTIVE', 'seed'),
(18, 'R18', '数据治理/档案',    'DEPT',      '建档/查重/编码/清洗/归档/借阅；不得改业务单据', 'ACTIVE', 'seed');

-- ---------- 权限项（MENU 1-9；API/BUTTON 10-62；S0 范围） ----------
INSERT INTO sys_permission (id, perm_code, perm_name, module, perm_type, parent_id, sort_no, status, created_by) VALUES
(1,  'menu:workbench',          '个人工作台', 'workbench', 'MENU', NULL, 1,  'ACTIVE', 'seed'),
(2,  'menu:notification',       '消息中心',   'notification', 'MENU', NULL, 2,  'ACTIVE', 'seed'),
(3,  'menu:system',             '系统管理',   'system',    'MENU', NULL, 9,  'ACTIVE', 'seed'),
(4,  'menu:system:user',        '用户管理',   'org',       'MENU', 3, 1,  'ACTIVE', 'seed'),
(5,  'menu:system:role',        '角色权限',   'org',       'MENU', 3, 2,  'ACTIVE', 'seed'),
(6,  'menu:system:dept',        '组织部门',   'org',       'MENU', 3, 3,  'ACTIVE', 'seed'),
(7,  'menu:system:dict',        '数据字典',   'system',    'MENU', 3, 4,  'ACTIVE', 'seed'),
(8,  'menu:system:config',      '系统参数',   'system',    'MENU', 3, 5,  'ACTIVE', 'seed'),
(9,  'menu:system:log',         '审计日志',   'system',    'MENU', 3, 6,  'ACTIVE', 'seed'),
(10, 'org:user:manage',          '用户管理',   'org',       'API', NULL, 1, 'ACTIVE', 'seed'),
(11, 'org:dept:manage',          '部门维护',   'org',       'API', NULL, 2, 'ACTIVE', 'seed'),
(12, 'org:role:manage',          '角色管理',   'org',       'API', NULL, 3, 'ACTIVE', 'seed'),
(13, 'org:dict:manage',          '字典维护',   'org',       'API', NULL, 4, 'ACTIVE', 'seed'),
(14, 'system:log:view',          '日志查看',   'system',    'API', NULL, 5, 'ACTIVE', 'seed'),
(15, 'system:config:manage',     '参数管理',   'system',    'API', NULL, 6, 'ACTIVE', 'seed'),
(16, 'system:integration:manage','集成管理',   'system',    'API', NULL, 7, 'ACTIVE', 'seed'),
(17, 'system:monitor:view',      '监控查看',   'system',    'API', NULL, 8, 'ACTIVE', 'seed'),
(18, 'system:theme:manage',      '默认主题管理','system',    'API', NULL, 9, 'ACTIVE', 'seed'),
(19, 'file:download',            '文件下载',   'file',      'API', NULL, 10, 'ACTIVE', 'seed'),
(20, 'file:upload',              '文件上传',   'file',      'API', NULL, 11, 'ACTIVE', 'seed'),
(21, 'profile:theme:switch',     '切换个人主题','profile',   'API', NULL, 12, 'ACTIVE', 'seed'),
(22, 'mdm:customer:create',      '客户建档',   'mdm',       'API', NULL, 1, 'ACTIVE', 'seed'),
(23, 'mdm:customer:edit',        '客户编辑',   'mdm',       'API', NULL, 2, 'ACTIVE', 'seed'),
(24, 'mdm:customer:merge',       '客户合并',   'mdm',       'API', NULL, 3, 'ACTIVE', 'seed'),
(25, 'mdm:project:create',       '项目建档',   'mdm',       'API', NULL, 4, 'ACTIVE', 'seed'),
(26, 'mdm:project:edit',         '项目编辑',   'mdm',       'API', NULL, 5, 'ACTIVE', 'seed'),
(27, 'mdm:project:change',       '项目变更',   'mdm',       'API', NULL, 6, 'ACTIVE', 'seed'),
(28, 'mdm:project:request',      '建档申请',   'mdm',       'API', NULL, 7, 'ACTIVE', 'seed'),
(29, 'mdm:material:create',      '物料建档',   'mdm',       'API', NULL, 8, 'ACTIVE', 'seed'),
(30, 'mdm:material:edit',        '物料编辑',   'mdm',       'API', NULL, 9, 'ACTIVE', 'seed'),
(31, 'mdm:material:disable',     '物料停用',   'mdm',       'API', NULL, 10, 'ACTIVE', 'seed'),
(32, 'mdm:supplier:manage',      '供应商建档', 'mdm',       'API', NULL, 11, 'ACTIVE', 'seed'),
(33, 'mdm:location:manage',      '货位建档',   'mdm',       'API', NULL, 12, 'ACTIVE', 'seed'),
(34, 'mdm:governance:run',       '治理执行',   'mdm',       'API', NULL, 13, 'ACTIVE', 'seed'),
(35, 'mdm:governance:rollback',  '治理回滚',   'mdm',       'API', NULL, 14, 'ACTIVE', 'seed'),
(36, 'opportunity:create',       '商机创建',   'opportunity','API', NULL, 1, 'ACTIVE', 'seed'),
(37, 'opportunity:edit',         '商机编辑',   'opportunity','API', NULL, 2, 'ACTIVE', 'seed'),
(38, 'opportunity:view',         '商机查看',   'opportunity','API', NULL, 3, 'ACTIVE', 'seed'),
(39, 'opportunity:list',         '商机列表',   'opportunity','API', NULL, 4, 'ACTIVE', 'seed'),
(40, 'opportunity:export',       '商机导出',   'opportunity','API', NULL, 5, 'ACTIVE', 'seed'),
(41, 'opportunity:convert',      '转合同',     'opportunity','API', NULL, 6, 'ACTIVE', 'seed'),
(42, 'opportunity:close',        '标记丢单',   'opportunity','API', NULL, 7, 'ACTIVE', 'seed'),
(43, 'opportunity:reassign',     '商机移交',   'opportunity','API', NULL, 8, 'ACTIVE', 'seed'),
(44, 'contract:create',          '合同创建',   'contract',  'API', NULL, 1, 'ACTIVE', 'seed'),
(45, 'contract:edit',            '合同编辑',   'contract',  'API', NULL, 2, 'ACTIVE', 'seed'),
(46, 'contract:view',            '合同查看',   'contract',  'API', NULL, 3, 'ACTIVE', 'seed'),
(47, 'contract:list',            '合同列表',   'contract',  'API', NULL, 4, 'ACTIVE', 'seed'),
(48, 'contract:export',          '合同导出',   'contract',  'API', NULL, 5, 'ACTIVE', 'seed'),
(49, 'contract:view_price',      '查看金额',   'contract',  'API', NULL, 6, 'ACTIVE', 'seed'),
(50, 'contract:upload',          '合同上传',   'contract',  'API', NULL, 7, 'ACTIVE', 'seed'),
(51, 'contract:review:submit',   '提交评审',   'contract',  'API', NULL, 8, 'ACTIVE', 'seed'),
(52, 'contract:review:approve',  '评审会签',   'contract',  'API', NULL, 9, 'ACTIVE', 'seed'),
(53, 'contract:seal',            '用印登记',   'contract',  'API', NULL, 10, 'ACTIVE', 'seed'),
(54, 'contract:confirm',         '确认归档',   'contract',  'API', NULL, 11, 'ACTIVE', 'seed'),
(55, 'contract:change',          '发起变更',   'contract',  'API', NULL, 12, 'ACTIVE', 'seed'),
(56, 'contract:change:approve',  '变更审批',   'contract',  'API', NULL, 13, 'ACTIVE', 'seed'),
(57, 'contract:dispatch',        '合同交底',   'contract',  'API', NULL, 14, 'ACTIVE', 'seed'),
(58, 'contract:payment:create',  '录入收款',   'contract',  'API', NULL, 15, 'ACTIVE', 'seed'),
(59, 'contract:payment:confirm', '财务确认',   'contract',  'API', NULL, 16, 'ACTIVE', 'seed'),
(60, 'contract:invoice:apply',   '开票申请',   'contract',  'API', NULL, 17, 'ACTIVE', 'seed'),
(61, 'contract:invoice:confirm', '开票确认',   'contract',  'API', NULL, 18, 'ACTIVE', 'seed'),
(62, 'contract:unfreeze',        '紧急解冻',   'contract',  'API', NULL, 19, 'ACTIVE', 'seed');

-- ---------- 角色 × 权限 ----------
-- R03 系统管理员：org/system/file/主题 + 系统菜单（无业务数据写权）
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 3, p.id, 'seed' FROM sys_permission p
WHERE p.id BETWEEN 1 AND 21;

-- R04 商务/销售：商机全量 + 合同录入链 + 建档申请 + 文件
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 4, p.id, 'seed' FROM sys_permission p
WHERE p.id IN (1, 2, 19, 21, 28) OR p.id BETWEEN 36 AND 48
   OR p.id IN (50, 51, 53, 54, 55, 57, 58, 60);

-- R05 商务主管：查看 + 审批类
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 5, p.id, 'seed' FROM sys_permission p
WHERE p.id IN (1, 2, 19, 21, 38, 39, 43, 46, 47, 52, 56, 61);

-- R15 财务：金额可见 + 收款/开票确认
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 15, p.id, 'seed' FROM sys_permission p
WHERE p.id IN (1, 2, 19, 21, 49, 59, 61);

-- R18 数据治理：主数据全量
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 18, p.id, 'seed' FROM sys_permission p
WHERE p.id IN (1, 2, 19, 21) OR p.id BETWEEN 22 AND 35;

-- 其余业务角色（R01/R02/R06-R14/R16/R17）：基础包（工作台/通知/文件下载/主题）
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT r.id, p.id, 'seed' FROM sys_role r
JOIN sys_permission p ON p.id IN (1, 2, 19, 21)
WHERE r.id IN (1, 2, 6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 17);

-- ---------- 用户（演示账号，密码统一 ProjLife@2026；admin 首登强改） ----------
INSERT INTO sys_user (id, user_code, username, real_name, password_hash, phone, dept_id, position, user_type, legal_entity_id, status, must_change_pwd, remark, created_by) VALUES
(1, 'E0001', 'admin@demo',  '系统管理员', '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000001', 2,  '系统管理', 'INTERNAL', 1, 'ACTIVE', 1, '首登强制改密', 'seed'),
(2, 'E0002', 'biz@demo',    '商务专员',   '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000002', 4,  '商务',     'INTERNAL', 1, 'ACTIVE', 0, '演示账号', 'seed'),
(3, 'E0003', 'boss@demo',   '商务主管',   '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000003', 4,  '商务主管', 'INTERNAL', 1, 'ACTIVE', 0, '演示账号', 'seed'),
(4, 'E0004', 'eng@demo',    '工程主管',   '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000004', 15, '工程管理', 'INTERNAL', 1, 'ACTIVE', 0, '演示账号', 'seed'),
(5, 'E0005', 'store@demo',  '仓管员',     '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000005', 12, '仓储',     'INTERNAL', 1, 'ACTIVE', 0, '演示账号（不得见价格）', 'seed'),
(6, 'E0006', 'fin@demo',    '财务主管',   '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000006', 13, '财务',     'INTERNAL', 1, 'ACTIVE', 0, '演示账号', 'seed'),
(7, 'E0007', 'worker@demo', '施工班组张', '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000007', 15, '安装工',   'WORKER',   1, 'ACTIVE', 0, '第三方施工（BR-08 仅小程序）', 'seed'),
(8, 'E0008', 'tech@demo',   '设计工程师', '$2b$10$o.B/R5qKQxULfD5K6fiMbuPce7LtcMqU1ET0vSkXSXbyhm5JlYZPe', '13800000008', 16, '研发设计', 'INTERNAL', 1, 'ACTIVE', 0, '演示账号', 'seed');

INSERT INTO sys_user_role (user_id, role_id, created_by) VALUES
(1, 3, 'seed'),
(2, 4, 'seed'),
(3, 5, 'seed'),
(4, 14, 'seed'),
(5, 11, 'seed'),
(6, 15, 'seed'),
(7, 17, 'seed'),
(8, 6, 'seed');

-- ---------- 九板（00 册 C-10 合并体系） ----------
INSERT INTO kb_board (id, board_code, board_name, board_level, dept_id, rhythm, sort_no, status, created_by) VALUES
(1, 'KB01', '公司经营总看板',     'TOTAL', 2,  'WEEKLY', 1, 'ACTIVE', 'seed'),
(2, 'KB02', '订单链日板',         'CHAIN', 4,  'DAILY',  2, 'ACTIVE', 'seed'),
(3, 'KB03', '供应链链日板',       'CHAIN', 10, 'DAILY',  3, 'ACTIVE', 'seed'),
(4, 'KB04', '生产链日板',         'CHAIN', 8,  'DAILY',  4, 'ACTIVE', 'seed'),
(5, 'KB05', '工程链日板',         'CHAIN', 15, 'DAILY',  5, 'ACTIVE', 'seed'),
(6, 'KB06', '研发职能周板',       'FUNC',  6,  'WEEKLY', 6, 'ACTIVE', 'seed'),
(7, 'KB07', '市场销售职能周板',   'FUNC',  3,  'WEEKLY', 7, 'ACTIVE', 'seed'),
(8, 'KB08', '财务职能周板',       'FUNC',  13, 'WEEKLY', 8, 'ACTIVE', 'seed'),
(9, 'KB09', '企管职能周板',       'FUNC',  14, 'WEEKLY', 9, 'ACTIVE', 'seed');

-- ---------- 系统参数与审批模板 ----------
INSERT INTO sys_config (config_key, config_value, config_group, is_encrypted, remark, created_by) VALUES
('app.theme.default', 't1', 'theme', 0, '系统默认主题（12 套预设 t1-t12，03 册 §6A.1），R03 可改', 'seed'),
('app.export.max-rows', '50000', 'export', 0, '导出行数上限（16 册 §十）', 'seed'),
('app.login.max-fail', '5', 'auth', 0, '登录失败锁定阈值（03 册）', 'seed'),
('approval.template.contract', '{"nodes":[{"code":"FIN","name":"财务评审"},{"code":"ENG","name":"工程评审"},{"code":"PROD","name":"生产评审"}],"mode":"COUNTERSIGN"}', 'approval', 0, '合同评审会签模板（S1 启用）', 'seed'),
('approval.template.eco', '{"nodes":[{"code":"TECH","name":"技术审批"},{"code":"QUALITY","name":"质量会签"}],"mode":"SEQUENCE"}', 'approval', 0, 'ECO 审批模板（S2/S3 启用）', 'seed');
