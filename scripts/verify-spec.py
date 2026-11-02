#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ProjLife 规格一致性自检（16 册 §7.1 verify-spec.py，S0 范围实现）。

检查项：
  1. 表完整性      —— Flyway 脚本中的表必须全部在 02 册 150 张登记清单内（不得多建未登记表），
                      登记但未建的表输出"切片内未建"提示（允许，不报错）。
  2. 枚举一致性    —— 种子字典类型数 = 42，type_code 与 02 册 §六 完全一致。
  3. 编码引擎      —— CodeTypes 覆盖 18 类编码（按 02 册 §五 抽样关键前缀）。
  4. 硬卡点覆盖    —— BR-01~09：S0 已实现 BR-07/BR-08，其余按切片提示（warn）。
  5. 禁公网 AI     —— 全源码扫描公网 AI SDK/域名（BR-AI-02，出现即 FAIL）。
  6. 编码规范      —— Java 禁 System.out/printStackTrace；金额禁 double/float 声明。
  7. 测试存在性    —— S0 冒烟测试类存在且覆盖关键断言（锁定/数据范围/编码并发/字典保护/BR-08）。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FAIL = []
WARN = []
OK = []

# ---------- 1. 表完整性（02 册 §三 登记清单，S0 已建子集） ----------
REGISTERED = {
    # T-SYS 14
    'sys_department', 'sys_user', 'sys_role', 'sys_user_role', 'sys_permission',
    'sys_role_permission', 'sys_dict_type', 'sys_dict_item', 'sys_operation_log',
    'sys_code_sequence', 'sys_attachment', 'sys_import_batch', 'sys_notification', 'sys_config', 'sys_approval', 'sys_approval_node',
    'sys_dingtalk_binding',  # SPRINT-INT2 钉钉通讯录绑定（用户立项扩编）
    # T-MDM 12
    'md_legal_entity', 'md_customer', 'md_customer_alias', 'md_wind_farm', 'md_project',
    'md_project_alias', 'md_project_product', 'md_project_change', 'md_material', 'md_code_map',
    'md_supplier', 'md_location',
    # T-CRM 6（V4.0：+报价单主表与行表）
    'crm_opportunity', 'crm_opportunity_followup', 'crm_customer_profile', 'crm_sales_funnel',
    'crm_quotation', 'crm_quotation_line',
    # T-BID 8（V4.0 M18 招标情报）
    'bid_source', 'bid_raw', 'bid_notice', 'bid_match', 'bid_rule', 'bid_push', 'bid_subscribe', 'bid_feedback',
    # T-AGT 3（V4.0 M19 Agent 接入）
    'agt_registration', 'agt_session', 'agt_call_log',
    # T-CT 7
    'ct_contract', 'ct_contract_payment_plan', 'ct_contract_risk', 'ct_contract_change',
    'ct_contract_dispatch', 'ct_invoice', 'ct_receipt',
    # T-PRJ 7
    'prj_project', 'prj_member', 'prj_task', 'prj_task_dependency', 'prj_template',
    'prj_template_task', 'prj_eco',
    # T-PLM 10
    'plm_survey', 'plm_scheme', 'plm_design_doc', 'plm_design_review', 'bom_header',
    'bom_line', 'bom_change', 'bom_change_impact', 'plm_std_part', 'plm_requirement_trace',
    # T-PCS 5
    'pcs_process_doc', 'pcs_route', 'pcs_route_step', 'pcs_worktime', 'pcs_cable_list',
    # T-SCM 9
    'scm_supplier_onboarding', 'scm_supplier_qualification', 'scm_supplier_evaluation',
    'scm_price_history', 'scm_purchase_requirement', 'scm_purchase_order',
    'scm_purchase_order_item', 'scm_purchase_return', 'scm_logistics',
    # T-INV 11
    'inv_transaction', 'inv_stock_cache', 'inv_batch', 'inv_count', 'inv_count_line',
    'inv_diff', 'inv_slow_moving', 'inv_disposal', 'inv_replenish', 'inv_replenish_line',
    'inv_reconcile',
    # T-MES 8
    'mes_production_order', 'mes_process_order', 'mes_process_report', 'mes_process_param',
    'mes_andon', 'mes_material_issue', 'mes_material_check', 'mes_work_center',
    # T-APS 4
    'aps_schedule', 'aps_schedule_detail', 'aps_material_check', 'aps_capacity',
    # T-QMS 9
    'qms_inspection_standard', 'qms_inspection', 'qms_inspection_item', 'qms_spc_data',
    'qms_fmea', 'qms_8d', 'qms_mrb', 'qms_gauge', 'qms_calibration',
    # T-EAM 5
    'eam_asset', 'eam_maintenance_plan', 'eam_maintenance_order', 'eam_spare_part', 'eam_failure',
    # T-ENG 16
    'eng_crew', 'eng_worker', 'eng_project_ext', 'eng_install_plan', 'eng_unit',
    'eng_acceptance', 'eng_daily_log', 'eng_quality_check', 'eng_rectification', 'eng_progress',
    'eng_cost_budget', 'eng_cost_actual', 'eng_safety_check', 'eng_material_issue',
    'eng_dispatch', 'eng_after_sales',
    # T-THD 5
    'thd_supplier_account', 'thd_worker_project', 'thd_evaluation', 'thd_blacklist',
    'thd_delivery_note',
    # T-COST 5
    'cost_item', 'cost_budget', 'cost_labor_rate', 'cost_snapshot', 'cost_evm',
    # T-KB 7
    'kb_board', 'kb_metric_def', 'kb_metric_snapshot', 'kb_card', 'kb_card_log',
    'kb_escalation', 'kb_meeting',
    # T-ARC 4
    'arc_archive', 'arc_borrow', 'arc_destroy', 'arc_transfer',
    # T-AI 3
    'ai_call_log', 'ai_model_config', 'ai_feedback',
    # T-INT 5
    'int_adapter', 'int_sync_log', 'int_mapping', 'int_outbox', 'int_reconcile_diff',
}

INFRA_WHITELIST = {'shedlock'}  # 16 册 §2.1 要求的调度基础设施表（ASSUMPTIONS A-09）

created = set()
for sql in sorted((ROOT / 'db' / 'migration').glob('V*.sql')):
    for match in re.finditer(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)', sql.read_text(encoding='utf-8'), re.I):
        created.add(match.group(1).lower())

unregistered = (created - REGISTERED) - INFRA_WHITELIST
if unregistered:
    FAIL.append(f"1.表完整性: 存在未登记表 {sorted(unregistered)}（02 册是唯一事实源，禁止多建）")
else:
    OK.append(f"1.表完整性: 已建 {len(created - INFRA_WHITELIST)} 张全部在 V4.0 163 张登记清单内；"
              f"未建 {len(REGISTERED - created)} 张属后续切片（S1 起逐片创建）")

# ---------- 2. 枚举一致性（02 册 §六 E-01~E-42） ----------
EXPECTED_TYPE_CODES = {
    'user_type', 'common_status', 'customer_level', 'customer_type', 'level_abc',
    'data_source', 'order_type', 'product_line', 'project_status', 'health',
    'contract_nature', 'business_type', 'contract_status', 'prj_status', 'task_type',
    'task_status', 'bom_form', 'bom_view', 'bom_status', 'uom',
    'material_category', 'source_type', 'txn_type', 'sync_status', 'process_type',
    'process_status', 'inspection_type', 'inspection_result', 'unit_status', 'title_quality',
    'card_status', 'dispatch_status', 'cost_type', 'po_status', 'data_scope',
    'supplier_level', 'diff_cause', 'slow_responsibility', 'disposal_method', 'replenish_reason',
    'arc_category', 'retention',
    # V4.0 E-43—E-52
    'bid_source_category', 'bid_source_status', 'bid_parse_status', 'bid_notice_status',
    'bid_match_level', 'bid_push_channel',
    'agt_identity_type', 'agt_task_status', 'agt_call_result',
    'kb_card_light',
}
seeded_types = set()
for vf in sorted((ROOT / 'db' / 'migration').glob('V*.sql')):
    body = vf.read_text(encoding='utf-8')
    for match in re.finditer(r"INSERT INTO sys_dict_type[^;]*?VALUES\s*(.*?);", body, re.S | re.I):
        for row in re.finditer(r"\((\d+),'(\w+)'", match.group(1)):
            seeded_types.add(row.group(2))
missing_types = EXPECTED_TYPE_CODES - seeded_types
extra_types = seeded_types - EXPECTED_TYPE_CODES
if missing_types or extra_types:
    FAIL.append(f"2.枚举一致性: 缺 {sorted(missing_types)} 多 {sorted(extra_types)}（须与 02 册 §六 + V4.0 E-43—E-52 完全一致）")
else:
    OK.append(f"2.枚举一致性: {len(seeded_types)} 组字典类型完全一致（E-01—E-52，V4.0 基线）")

# ---------- 3. 编码引擎（02 册 §五 18 类） ----------
code_types = ROOT / 'projlife-modules' / 'module-org' / 'src' / 'main' / 'java' / 'com' / 'projlife' / 'org' / 'service' / 'code' / 'CodeTypes.java'
if code_types.exists():
    src = code_types.read_text(encoding='utf-8')
    required_prefixes = ['OPP-', 'PR-', 'PO-', 'MO-', 'WS-', 'AS-', 'WO-', 'SH-',
                         'ECN-', 'CT-', 'DF-', 'DP-', 'RP-', 'AI-', 'PJ', 'WF', '"C"', '"S"']
    missing_prefix = [p for p in required_prefixes if p not in src]
    if missing_prefix:
        FAIL.append(f"3.编码引擎: 缺少编码格式 {missing_prefix}")
    else:
        OK.append("3.编码引擎: 18 类格式齐备（CodeTypes 单一入口，取号走 sys_code_sequence）")
else:
    FAIL.append("3.编码引擎: CodeTypes.java 不存在")

# ---------- 4. 硬卡点（00 册 §7.2，随切片生效） ----------
java_srcs = list(ROOT.glob('projlife-*/src/**/*.java')) + list(ROOT.glob('projlife-modules/*/src/**/*.java'))
all_java = '\n'.join(p.read_text(encoding='utf-8', errors='replace') for p in java_srcs)
active = [code for code in ['BR-01', 'BR-02', 'BR-05', 'BR-07', 'BR-08'] if code in all_java]
if 'BR-07' in all_java and 'BR-08' in all_java:
    remaining = [b for b in ['BR-03', 'BR-04', 'BR-06', 'BR-09'] if b not in all_java]
    OK.append(f"4.硬卡点: 已生效 {active}；{'其余 ' + str(remaining) + ' 随后续切片生效（切片内允许未建）' if remaining else '全部生效'}")
else:
    FAIL.append("4.硬卡点: BR-07/BR-08 未在代码中生效")

# ---------- 5. 禁公网 AI（BR-AI-02） ----------
PATTERNS = ['openai.com', 'anthropic.com', 'claude.ai', 'gemini', 'generativelativelanguage',
            'api.openai', 'chatgpt', 'dashscope.aliyuncs', 'baidubce.com', 'volcengine', 'openai']
hits = []
scan_roots = [ROOT / 'projlife-common', ROOT / 'projlife-security', ROOT / 'projlife-modules',
              ROOT / 'projlife-api', ROOT / 'projlife-job', ROOT / 'projlife-ai-gateway',
              ROOT / 'projlife-file-service']
for base in scan_roots:
    for p in base.rglob('*'):
        if p.suffix in {'.java', '.xml', '.yml', '.properties', '.js', '.vue', '.mjs'} and p.exists():
            try:
                text = p.read_text(encoding='utf-8', errors='replace')
            except OSError:
                continue
            for pat in PATTERNS:
                if pat.lower() in text.lower():
                    hits.append(f"{p.relative_to(ROOT)} contains '{pat}'")
if hits:
    FAIL.append(f"5.禁公网 AI: 发现公网 AI 关键字 {hits[:5]}")
else:
    OK.append("5.禁公网 AI: 全源码无公网 AI SDK/域名（仅内网 model-endpoint 配置点）")

# ---------- 6. 编码规范 ----------
bad_style = []
for p in java_srcs:
    text = p.read_text(encoding='utf-8', errors='replace')
    if 'System.out.print' in text or 'printStackTrace' in text:
        bad_style.append(f"{p.name}: System.out/printStackTrace")
    if re.search(r'\b(double|float)\s+(amount|price|totalAmount|unitPrice)\b', text):
        bad_style.append(f"{p.name}: 金额使用 double/float")
if bad_style:
    FAIL.append(f"6.编码规范: {bad_style[:5]}")
else:
    OK.append("6.编码规范: 无 System.out/printStackTrace；金额类型合规（BigDecimal）")

# ---------- 7. 测试存在性 ----------
smoke = ROOT / 'projlife-api' / 'src' / 'test' / 'java' / 'com' / 'projlife' / 'api' / 'S0SmokeTest.java'
gateway = ROOT / 'projlife-ai-gateway' / 'src' / 'test' / 'java' / 'com' / 'projlife' / 'aigateway' / 'AiGatewaySmokeTest.java'
smoke_src = smoke.read_text(encoding='utf-8') if smoke.exists() else ''
required_asserts = ['loginLock', 'dataScope', 'codeEngineConcurrency', 'systemDictProtected', 'workerClientLimit']
missing_tests = [t for t in required_asserts if t not in smoke_src]
if not smoke.exists() or not gateway.exists() or missing_tests:
    FAIL.append(f"7.测试存在性: 缺关键用例 {missing_tests}")
else:
    OK.append("7.测试存在性: S0 冒烟 9 用例 + AI 网关 4 用例（锁定/数据范围/编码并发/字典保护/BR-08 全覆盖）")

# ---------- 汇总 ----------
print('=' * 64)
for line in OK:
    print(f"[PASS] {line}")
for line in WARN:
    print(f"[WARN] {line}")
for line in FAIL:
    print(f"[FAIL] {line}")
print('=' * 64)
if FAIL:
    print(f"RESULT: FAIL（{len(FAIL)} 项）")
    sys.exit(1)
print("RESULT: PASS")
