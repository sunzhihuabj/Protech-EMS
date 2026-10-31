#!/usr/bin/env python3
"""F1 修复：为所有缺失 @RequirePermission 的 Controller 端点补注解。"""
import glob, re, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PERM_MAP = {
    'ContractController': {'POST': 'contract:create', 'GET': 'contract:view',
        'ov': {'/review/submit': 'contract:review:submit', '/seal': 'contract:seal',
               '/filing': 'contract:confirm', '/unfreeze': 'contract:confirm',
               '/receipts': 'cost:view', '/invoices': 'cost:view', '/ai/': 'contract:edit'}},
    'OpportunityController': {'POST': 'opportunity:create', 'GET': 'opportunity:view',
        'ov': {'/convert': 'opportunity:edit', '/lose': 'opportunity:edit', '/reopen': 'opportunity:edit'}},
    'QuotationController': {'POST': 'opportunity:create', 'GET': 'opportunity:view',
        'ov': {'/submit': 'opportunity:edit', '/decide': 'opportunity:edit'}},
    'PlmController': {'POST': 'bom:create', 'GET': 'bom:view',
        'ov': {'/release': 'bom:release', '/publish': 'bom:release', '/qc-batch': 'bom:release'}},
    'MdmController': {'POST': 'mdm:material:create', 'GET': 'mdm:customer:view',
        'ov': {'/projects': 'mdm:project:view', '/materials': 'mdm:material:create',
               '/deactivate': 'mdm:customer:create', '/resolve': 'mdm:customer:view'}},
    'ProjectController': {'POST': 'project:create', 'GET': 'project:view',
        'ov': {'/gate': 'project:edit', '/ecos': 'project:edit', '/members': 'project:edit'}},
    'MesController': {'POST': 'mes:order:create', 'GET': 'mes:order:view',
        'ov': {'/start': 'mes:process:work', '/finish': 'mes:process:work',
               '/qc': 'mes:qc:release', '/andon': 'mes:andon:raise', '/routes': 'mes:order:create'}},
    'InventoryController': {'POST': 'inv:scan', 'GET': 'inv:scan',
        'ov': {'/replenish': 'inv:replenish:create', '/approve': 'inv:replenish:approve',
               '/retry': 'inv:count:manage', '/reconcile': 'inv:count:manage'}},
    'QmsController': {'POST': 'qms:inspect', 'GET': 'qms:inspect',
        'ov': {'/8ds': 'qms:8d:manage', '/close': 'qms:8d:manage',
               '/gauges': 'qms:gauge:manage', '/calibrate': 'qms:gauge:manage'}},
    'EngController': {'POST': 'eng:dispatch', 'GET': 'eng:dispatch',
        'ov': {'/workers': 'eng:crew:manage', '/activate': 'eng:crew:manage',
               '/units': 'eng:install', '/rectifications': 'eng:rect:manage'}},
    'EngRecordController': {'POST': 'eng:log', 'GET': 'eng:log',
        'ov': {'/quality-checks': 'eng:install', '/material-issues': 'eng:expense',
               '/after-sales': 'eng:after:manage'}},
    'ThdPortalController': {'POST': 'thd:portal', 'GET': 'thd:portal', 'ov': {}},
    'BidController': {'POST': 'bid:notice:manual', 'GET': 'bid:notice:view',
        'ov': {'/sources': 'bid:source:manage', '/review': 'bid:source:manage',
               '/convert': 'bid:notice:convert', '/subscribe': 'bid:push:subscribe',
               '/push': 'bid:push:subscribe', '/score': 'bid:notice:view'}},
    'ApprovalController': {'POST': 'sys:approval:act', 'GET': 'sys:approval:act', 'ov': {}},
    'ArcController': {'POST': 'arc:manage', 'GET': 'arc:manage',
        'ov': {'/appraisal-scan': None}},
    'CostKbController': {'POST': 'cost:budget', 'GET': 'cost:view',
        'ov': {'/kb/cards': 'kb:card:manage', '/block': 'kb:card:manage',
               '/close': 'kb:card:manage', '/meetings': 'kb:card:manage',
               '/pdf': 'kb:card:manage', '/escalate': None, '/appraisal-scan': None}},
    'KbMetricController': {'POST': 'kb:metric:def', 'GET': 'kb:metric:def', 'ov': {}},
    'IntegrationController': {'POST': 'sys:config', 'PUT': 'sys:config',
        'GET': 'sys:config', 'ov': {}},
    'EamController': {'POST': 'qms:inspect', 'GET': 'qms:inspect', 'ov': {}},
    'InventoryCountController': {'POST': 'inv:count:manage', 'GET': 'inv:count:manage', 'ov': {}},
    'SlowMovingController': {'POST': 'inv:slow:manage', 'GET': 'inv:slow:manage', 'ov': {}},
    'ApsController': {'POST': 'aps:schedule', 'GET': 'aps:schedule', 'ov': {}},
    'InternalJobsController': {'POST': None, 'GET': None, 'ov': {}},
}

modified = 0
for f in glob.glob(os.path.join(ROOT, 'projlife-modules', '*', 'src', 'main', 'java',
                                'com', 'projlife', '*', 'controller', '*.java')):
    class_name = os.path.basename(f).replace('.java', '')
    if class_name not in PERM_MAP:
        continue
    cfg = PERM_MAP[class_name]
    src = open(f, encoding='utf-8').read()
    lines = src.split('\n')
    new_lines = list(lines)
    changed = False

    for i, line in enumerate(lines):
        if not re.match(r'\s+@(Get|Post|Put|Delete)Mapping', line):
            continue
        k = i - 1
        has_perm = False
        while k >= 0 and lines[k].strip().startswith('@'):
            if 'RequirePermission' in lines[k]:
                has_perm = True
            k -= 1
        if has_perm:
            continue

        mapping = re.search(r'@((?:Get|Post|Put|Delete)Mapping)\s*(?:\("([^"]*)"\))?', line)
        http_method = mapping.group(1).upper() if mapping else 'GET'
        path = mapping.group(2) if mapping and mapping.group(2) else ''

        # 内部端点跳过
        skip_paths = ['/internal', '/escalate', '/overdue-scan', '/age-scan',
                      '/appraisal-scan', '/lock-overdue']
        if any(sp in path for sp in skip_paths):
            continue

        # 查 override
        perm = None
        for key, p_val in cfg.get('ov', {}).items():
            if key in path:
                perm = p_val
                break

        # 无 override 用 default
        if perm is None:
            if http_method in ('POST', 'PUT', 'DELETE'):
                perm = cfg.get('POST') or cfg.get('default_write')
            else:
                perm = cfg.get('GET') or cfg.get('default_read')

        if perm is None:
            continue

        # 检查下一行是否已有 RequirePermission（可能跨行注解）
        j = i + 1
        if j < len(new_lines) and 'RequirePermission' in new_lines[j]:
            continue

        # 插入注解（在 Mapping 行前）
        indent = re.match(r'(\s*)', line).group(1)
        new_lines.insert(i, f'{indent}@RequirePermission("{perm}")')
        changed = True
        modified += 1

    if changed:
        new_src = '\n'.join(new_lines)
        # 需要加 import
        if 'import com.projlife.security.auth.RequirePermission;' not in new_src:
            new_src = new_src.replace(
                'import org.springframework.web.bind.annotation.RequestMapping;',
                'import com.projlife.security.auth.RequirePermission;\nimport org.springframework.web.bind.annotation.RequestMapping;')
        open(f, 'w', encoding='utf-8', newline='').write(new_src)

print(f'F1 修复完成：{modified} 个端点补 @RequirePermission')
