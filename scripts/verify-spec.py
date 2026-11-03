#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ProjLife 规格一致性自检（16 册 §7.1 verify-spec.py，S0 范围实现）。

检查项（SPRINT-UID-R17 起第 3/4/6/7 项升级为域名级匹配：先剥离 Java 注释，
   再按代码语义上下文命中——写进注释/测试文案的敏感词不再算数，防"改注释过门禁"）：
  1. 表完整性      —— Flyway 脚本中的表必须全部在 02 册 150 张登记清单内（不得多建未登记表），
                      登记但未建的表输出"切片内未建"提示（允许，不报错）。
  2. 枚举一致性    —— 种子字典类型数 = 42，type_code 与 02 册 §六 完全一致。
  3. 编码引擎      —— CodeTypes 覆盖 18 类编码（字符串字面量位置匹配，注释不计）。
  4. 硬卡点覆盖    —— BR-01~09：必须出现在主源码（排除 src/test）BizException/blockCode 上下文行。
  5. 禁公网 AI     —— 全源码扫描公网 AI SDK/域名（BR-AI-02，出现即 FAIL）。
  6. 编码规范      —— Java 禁 System.out/printStackTrace；金额禁 double/float（语义命名 widened）。
  7. 测试存在性    —— S0 冒烟测试类存在，关键用例须以方法声明形式存在且文件含 @Test。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FAIL = []
WARN = []
OK = []


def strip_java_comments(text: str) -> str:
    """剥离 // 与 /* */ 注释（保留字符串/字符字面量内容），返回纯代码文本。
    域名级检查的前提：注释里出现的敏感词不再参与匹配（防注释绕过）。"""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ''
        if c == '/' and nxt == '/':
            while i < n and text[i] != '\n':
                i += 1
        elif c == '/' and nxt == '*':
            i += 2
            while i + 1 < n and not (text[i] == '*' and text[i + 1] == '/'):
                i += 1
            i += 2
        elif c == '"':
            out.append(c)
            i += 1
            while i < n and text[i] != '"':
                if text[i] == '\\':
                    i += 1
                out.append(text[i] if i < n else '')
                i += 1
            out.append('"')
            i += 1
        elif c == "'":
            out.append(c)
            i += 1
            while i < n and text[i] != "'":
                if text[i] == '\\':
                    i += 1
                out.append(text[i] if i < n else '')
                i += 1
            out.append("'")
            i += 1
        else:
            out.append(c)
            i += 1
    return ''.join(out)

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

# ---------- 3. 编码引擎（02 册 §五 18 类；域名级：仅认常量定义行，注释不计） ----------
code_types = ROOT / 'projlife-modules' / 'module-org' / 'src' / 'main' / 'java' / 'com' / 'projlife' / 'org' / 'service' / 'code' / 'CodeTypes.java'
if code_types.exists():
    src = strip_java_comments(code_types.read_text(encoding='utf-8'))
    required_prefixes = ['OPP-', 'PR-', 'PO-', 'MO-', 'WS-', 'AS-', 'WO-', 'SH-',
                         'ECN-', 'CT-', 'DF-', 'DP-', 'RP-', 'AI-', 'PJ', 'WF', '"C"', '"S"']
    # 前缀必须出现在字符串字面量位置（形如 "OPP-" 或 'C'），裸词/注释不再算数
    missing_prefix = [p for p in required_prefixes
                      if f'"{p.strip(chr(34))}"' not in src and f"'{p.strip(chr(34))}'" not in src]
    if missing_prefix:
        FAIL.append(f"3.编码引擎: 缺少编码格式 {missing_prefix}")
    else:
        OK.append("3.编码引擎: 18 类格式齐备（CodeTypes 单一入口，取号走 sys_code_sequence；域名级=字符串字面量匹配）")
else:
    FAIL.append("3.编码引擎: CodeTypes.java 不存在")

# ---------- 4. 硬卡点（00 册 §7.2；域名级：主源码 throw/blockCode 上下文行，注释与测试不计） ----------
main_srcs = [p for p in list(ROOT.glob('projlife-*/src/main/**/*.java'))
             + list(ROOT.glob('projlife-modules/*/src/main/**/*.java'))
             if 'src/test' not in str(p)]
BIZ_RE = re.compile(r'(BizException|blockCode)')
br_lines = []
for p in main_srcs:
    for ln in strip_java_comments(p.read_text(encoding='utf-8', errors='replace')).splitlines():
        if BIZ_RE.search(ln):
            br_lines.append(ln)
found_brs = {code for code in
             ['BR-01', 'BR-02', 'BR-03', 'BR-04', 'BR-05', 'BR-06', 'BR-07', 'BR-08', 'BR-09']
             if any(code in ln for ln in br_lines)}
if 'BR-07' in found_brs and 'BR-08' in found_brs:
    remaining = [b for b in ['BR-01', 'BR-02', 'BR-03', 'BR-04', 'BR-05', 'BR-06', 'BR-09']
                 if b not in found_brs]
    OK.append(f"4.硬卡点: 域名级命中 {sorted(found_brs)}（主源码 BizException/blockCode 行）；"
              f"{'其余 ' + str(remaining) + ' 随后续切片生效' if remaining else '全部生效'}")
else:
    FAIL.append("4.硬卡点: BR-07/BR-08 未在主源码 BizException/blockCode 上下文生效（域名级检查）")

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

# ---------- 6. 编码规范（域名级：去注释后按代码语义匹配；金额名 widened） ----------
java_srcs = [p for p in list(ROOT.glob('projlife-*/src/**/*.java'))
             + list(ROOT.glob('projlife-modules/*/src/**/*.java'))
             if 'target' not in str(p)]
bad_style = []
MONEY_NAME = re.compile(r'\b(double|float)\s+\w*(amount|price|amt|cost|fee|money|total)\w*\b', re.I)
for p in java_srcs:
    text = strip_java_comments(p.read_text(encoding='utf-8', errors='replace'))
    if 'System.out.print' in text or 'printStackTrace' in text:
        bad_style.append(f"{p.name}: System.out/printStackTrace")
    if MONEY_NAME.search(text):
        bad_style.append(f"{p.name}: 金额使用 double/float")
if bad_style:
    FAIL.append(f"6.编码规范: {bad_style[:5]}")
else:
    OK.append("6.编码规范: 无 System.out/printStackTrace；金额类型合规（BigDecimal；域名级=去注释+语义命名匹配）")

# ---------- 7. 测试存在性（域名级：方法名必须以方法声明形式存在 + 文件含 @Test） ----------
smoke = ROOT / 'projlife-api' / 'src' / 'test' / 'java' / 'com' / 'projlife' / 'api' / 'S0SmokeTest.java'
gateway = ROOT / 'projlife-ai-gateway' / 'src' / 'test' / 'java' / 'com' / 'projlife' / 'aigateway' / 'AiGatewaySmokeTest.java'
smoke_src = smoke.read_text(encoding='utf-8') if smoke.exists() else ''
required_asserts = ['loginLock', 'dataScope', 'codeEngineConcurrency', 'systemDictProtected', 'workerClientLimit']
missing_tests = [t for t in required_asserts
                 if not re.search(rf'\bvoid\s+{t}\s*\(', smoke_src)]
has_tests = '@Test' in smoke_src
if not smoke.exists() or not gateway.exists() or missing_tests or not has_tests:
    FAIL.append(f"7.测试存在性: 缺关键用例 {missing_tests}（域名级=方法声明匹配）或无 @Test")
else:
    OK.append("7.测试存在性: S0 冒烟关键用例方法声明齐备 + AI 网关用例在位（锁定/数据范围/编码并发/字典保护/BR-08）")

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
