-- V23: SPRINT-OPS1 幽灵权限码补种（注解存在但 sys_permission 无此码 → 恒 2002 死端点修复）
-- 授权规则：读码授予"持同级写码的角色"，R03/R20 全量。

INSERT IGNORE INTO sys_permission (id, perm_code, perm_name, module, perm_type, sort_no, status, created_by) VALUES
 (146, 'mdm:customer:view',  '客户查看',       'mdm',    'ACTION', 200, 'ACTIVE', 'V23'),
 (147, 'mdm:material:view',  '物料查看',       'mdm',    'ACTION', 201, 'ACTIVE', 'V23'),
 (148, 'mdm:project:view',   '项目主数据查看', 'mdm',    'ACTION', 202, 'ACTIVE', 'V23'),
 (149, 'mes:order:view',     '生产订单查看',   'mes',    'ACTION', 203, 'ACTIVE', 'V23'),
 (150, 'qms:inspection',     '检验查看/SPC',   'qms',    'ACTION', 204, 'ACTIVE', 'V23'),
 (151, 'sys:config',         '系统配置操作',   'system', 'ACTION', 205, 'ACTIVE', 'V23');

-- 同伴写码持有者自动获得对应读码
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 146, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'mdm:customer:edit'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 146);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 147, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'mdm:material:edit'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 147);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 148, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'mdm:project:edit'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 148);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 149, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'mes:order:create'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 149);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 150, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'qms:inspect'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 150);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT DISTINCT rrp.role_id, 151, 'V23' FROM sys_role_permission rrp JOIN sys_permission p ON p.id = rrp.perm_id
WHERE p.perm_code = 'system:config:manage'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = rrp.role_id AND x.perm_id = 151);

-- R03（管理员演示角色）与 R20（Administrator 全权限）补全 6 码
INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 3, p.id, 'V23' FROM sys_permission p WHERE p.id BETWEEN 146 AND 151
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = 3 AND x.perm_id = p.id);

INSERT INTO sys_role_permission (role_id, perm_id, created_by)
SELECT 20, p.id, 'V23' FROM sys_permission p WHERE p.id BETWEEN 146 AND 151
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission x WHERE x.role_id = 20 AND x.perm_id = p.id);

-- SPRINT-KPI1（F7）：自动取数目录的 10 个指标定义种子（is_manual=0；阈值默认 A-35 口径 0.9/0.8 由 judge 内置，显式写入便于调整）
INSERT IGNORE INTO kb_metric_def (id, metric_code, board_id, metric_name, formula, source, target, threshold_json, is_manual, created_by) VALUES
 (9001, 'S17.MO.DELIVERY', 2, '订单准时交付率',   'COMPLETED 订单数 / 全部订单',        'mes_production_order', 100.00, '{"yellowRatio":0.9,"redRatio":0.8,"precision":0}', 0, 'V23'),
 (9002, 'MES.FINISH.RATE', 2, '生产完工率',       'Σfinished_qty / Σplan_qty',          'mes_production_order', 100.00, '{"yellowRatio":0.9,"redRatio":0.8,"precision":1}', 0, 'V23'),
 (9003, 'QMS.FIRSTPASS',   3, '一次合格率',       'PASS 且零缺陷检验 / 全部检验',        'qms_inspection',       100.00, '{"yellowRatio":0.9,"redRatio":0.8,"precision":1}', 0, 'V23'),
 (9004, 'CT.AMOUNT.MONTH', 1, '当月签约额(万)',   'Σamount（当月）/ 10000',             'ct_contract',          NULL,   NULL, 0, 'V23'),
 (9005, 'CT.COUNT.MONTH',  1, '当月签约数',       'COUNT（当月）',                      'ct_contract',          NULL,   NULL, 0, 'V23'),
 (9006, 'PRJ.PROGRESS.AVG',1, '项目平均进度%',    'AVG(progress_pct)',                  'prj_project',          NULL,   NULL, 0, 'V23'),
 (9007, 'PRJ.OVERDUE.COUNT',1,'逾期项目数',      'plan_end 已过未完工项目计数',        'prj_project',          0.00,   '{"maxValue":0,"precision":0}', 0, 'V23'),
 (9008, 'INV.SLOW.RATE',   3, '呆滞批次率%',      '库龄>180 天在库批次 / 在库批次',     'inv_batch',            5.00,   '{"yellowRatio":1.0,"redRatio":2.0,"precision":1}', 0, 'V23'),
 (9009, 'KB.CARD.RED.COUNT',1,'看板当前红灯数',  'RED/BLOCKED/ESCALATED 卡计数',       'kb_card',              0.00,   '{"maxValue":3,"precision":0}', 0, 'V23'),
 (9010, 'KB.CARD.CLOSE.RATE',1,'红灯闭环率%',    'CLOSED / (红灯+闭环) 卡计数',        'kb_card',              90.00,  '{"yellowRatio":0.9,"redRatio":0.8,"precision":0}', 0, 'V23'),
 (9011, 'CRM.FUNNEL.AMOUNT',4,'在途商机额(万)',   'Σexpect_amount（未输未转）',         'crm_opportunity',      NULL,   NULL, 0, 'V23'),
 (9012, 'CRM.WIN.RATE',    4, '商机赢单率%',      'CONVERTED / (CONVERTED+LOST)',       'crm_opportunity',      50.00,  '{"yellowRatio":0.9,"redRatio":0.8,"precision":0}', 0, 'V23'),
 (9013, 'BID.PIPELINE.AMOUNT',5,'在途标讯额(万)','Σbudget_amount（未截止）',           'bid_notice',           NULL,   NULL, 0, 'V23'),
 (9014, 'SCM.PO.PENDING',  3, '在途采购单数',     '未完工采购单计数',                   'scm_purchase_order',   0.00,   '{"maxValue":10,"precision":0}', 0, 'V23'),
 (9015, 'QMS.8D.OPEN',     3, '未关闭 8D 数',     'OPEN 8D 计数',                       'qms_8d',               0.00,   '{"maxValue":2,"precision":0}', 0, 'V23'),
 (9016, 'CT.PAYMENT.RATE', 1, '回款进度%',        'Σactual_amount / Σamount',           'ct_contract_payment_plan', 100.00, '{"yellowRatio":0.9,"redRatio":0.8,"precision":1}', 0, 'V23');
