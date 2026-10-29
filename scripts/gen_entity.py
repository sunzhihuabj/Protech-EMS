#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实体/Mapper 代码生成器（工程内部工具）。
字段规格集中在本文件，来源 02 册 §4.2-4.4 与 §4.20 简明字段表——保证 DDL/实体一致。
用法：python scripts/gen_entity.py [--check]  （--check 只校验生成物是否最新）
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 类型映射：MySQL 列类型 -> Java
JAVA = {
    'VARCHAR': 'String', 'BIGINT': 'Long', 'DECIMAL': 'java.math.BigDecimal',
    'DATETIME': 'java.time.LocalDateTime', 'DATE': 'java.time.LocalDate',
    'TINYINT': 'Integer', 'INT': 'Integer', 'JSON': 'String',
}

# 模块注册：key -> (maven artifactId 所在目录, java 包段)
MODULES = {
    'mdm': 'projlife-modules/module-mdm',
    'crm': 'projlife-modules/module-crm',
    'ct': 'projlife-modules/module-contract',
}

# 表规格：表名 -> (模块, 实体名, 是否乐观锁, 字段列表 [(列名, 类型, 注释)])
TABLES = {
    # ---------------- T-MDM 主数据 ----------------
    'md_legal_entity': ('mdm', 'MdLegalEntity', False, [
        ('entity_code', 'VARCHAR', '主体码 PRT/TLD'),
        ('entity_name', 'VARCHAR', '法人名称'),
        ('tax_no', 'VARCHAR', '税号'),
        ('address', 'VARCHAR', '注册地址'),
        ('bank_info', 'VARCHAR', '开户信息'),
        ('invoice_header', 'VARCHAR', '开票抬头'),
    ]),
    'md_customer': ('mdm', 'MdCustomer', True, [
        ('customer_code', 'VARCHAR', 'C{4位}，不可改'),
        ('customer_name', 'VARCHAR', '规范全称（与营业执照一致）'),
        ('short_name', 'VARCHAR', '简称'),
        ('parent_id', 'BIGINT', '上级（区域/项目公司→集团）'),
        ('level', 'VARCHAR', 'E-03 GROUP/REGION/PROJECT'),
        ('customer_type', 'VARCHAR', 'E-04 OWNER/OEM/RELATED/OTHER'),
        ('group_name', 'VARCHAR', '集团归并名（142→14）'),
        ('credit_code', 'VARCHAR', '统一社会信用代码'),
        ('contact_person', 'VARCHAR', '联系人（脱敏展示）'),
        ('contact_phone', 'VARCHAR', '联系电话（脱敏展示）'),
        ('address', 'VARCHAR', '地址'),
        ('level_abc', 'VARCHAR', 'E-05 客户分级'),
        ('credit_score', 'DECIMAL', '信用分'),
        ('credit_limit', 'DECIMAL', '信用额度（元）'),
        ('is_internal', 'TINYINT', '内部关联方（特固兰=1）'),
        ('status', 'VARCHAR', 'E-02'),
        ('source', 'VARCHAR', 'E-06 数据来源'),
        ('remark', 'VARCHAR', '备注'),
    ]),
    'md_customer_alias': ('mdm', 'MdCustomerAlias', False, [
        ('customer_id', 'BIGINT', '主体 id'),
        ('alias_name', 'VARCHAR', '别名（历史写法/简称/错别字）'),
        ('alias_type', 'VARCHAR', '别名类型'),
        ('source', 'VARCHAR', '来源'),
    ]),
    'md_wind_farm': ('mdm', 'MdWindFarm', False, [
        ('farm_code', 'VARCHAR', 'WF{YYYY}-{NNN}'),
        ('farm_name', 'VARCHAR', '风场全称（业主规范）'),
        ('province', 'VARCHAR', '省'),
        ('city', 'VARCHAR', '市'),
        ('county', 'VARCHAR', '县'),
        ('owner_id', 'BIGINT', '业主（md_customer）'),
        ('capacity_mw', 'DECIMAL', '装机容量 MW'),
        ('phase_count', 'INT', '期次数'),
        ('status', 'VARCHAR', 'E-02'),
    ]),
    'md_project': ('mdm', 'MdProject', True, [
        ('project_code', 'VARCHAR', 'PJ{YYYY}-{NNNN} 永不复用'),
        ('std_name', 'VARCHAR', '标准名 = 业主规范名－地名（去尾缀）－期次'),
        ('short_name', 'VARCHAR', '常用简称（现场口语）'),
        ('project_name', 'VARCHAR', '项目业务名'),
        ('customer_id', 'BIGINT', '签约主体（合同甲方）'),
        ('owner_id', 'BIGINT', '业主（初装可空 owner_pending=1）'),
        ('oem_id', 'BIGINT', '整机厂（纯技改可空）'),
        ('legal_entity_id', 'BIGINT', '我方签约主体'),
        ('order_type', 'VARCHAR', 'E-07 订单类型'),
        ('farm_id', 'BIGINT', '风场（排产单可空 farm_pending=1）'),
        ('farm_name_raw', 'VARCHAR', '风场全称原文'),
        ('phase', 'VARCHAR', '期次'),
        ('product_lines', 'JSON', 'E-08 产品线数组'),
        ('turbine_model', 'VARCHAR', '机型'),
        ('turbine_qty', 'INT', '机组数量'),
        ('contract_amount', 'DECIMAL', '合同额（元）'),
        ('set_qty', 'DECIMAL', '台数/套数 >0'),
        ('warranty_start', 'DATE', '质保起'),
        ('warranty_end', 'DATE', '质保止（>start）'),
        ('warranty_months', 'INT', '质保月数'),
        ('eng_owner_id', 'BIGINT', '工程责任人（sys_user）'),
        ('biz_owner_id', 'BIGINT', '商务责任人（sys_user）'),
        ('svc_owner_id', 'BIGINT', '售后责任人（质保期内必填）'),
        ('status', 'VARCHAR', 'E-09 项目状态'),
        ('health', 'VARCHAR', 'E-10 健康度'),
        ('owner_pending', 'TINYINT', '业主待补全标记'),
        ('farm_pending', 'TINYINT', '风场待补全标记'),
        ('remark', 'VARCHAR', '备注'),
    ]),
    'md_project_alias': ('mdm', 'MdProjectAlias', False, [
        ('md_project_id', 'BIGINT', '项目档案 id'),
        ('alias_name', 'VARCHAR', '别名'),
        ('alias_type', 'VARCHAR', '历史写法/口语/错别字'),
        ('source', 'VARCHAR', '来源'),
    ]),
    'md_project_product': ('mdm', 'MdProjectProduct', False, [
        ('md_project_id', 'BIGINT', '项目档案 id'),
        ('product_line', 'VARCHAR', 'E-08 产品线'),
        ('contract_id', 'BIGINT', '关联合同'),
        ('set_qty', 'DECIMAL', '台数/套数'),
        ('warranty_start', 'DATE', '质保起'),
        ('warranty_end', 'DATE', '质保止'),
        ('remark', 'VARCHAR', '备注'),
    ]),
    'md_project_change': ('mdm', 'MdProjectChange', False, [
        ('change_no', 'VARCHAR', '变更单号'),
        ('md_project_id', 'BIGINT', '项目档案 id'),
        ('change_type', 'VARCHAR', 'FARM_FILL/ENTITY_CHANGE/PHASE_SPLIT'),
        ('before_json', 'JSON', '变更前快照'),
        ('after_json', 'JSON', '变更后快照'),
        ('approved_by', 'BIGINT', '审批人'),
        ('approved_at', 'DATETIME', '审批时间'),
    ]),
    'md_material': ('mdm', 'MdMaterial', False, [
        ('material_code', 'VARCHAR', '8 位数字主干（字母前缀冻结新增）'),
        ('material_name', 'VARCHAR', '物料名称'),
        ('spec', 'VARCHAR', '规格'),
        ('unit', 'VARCHAR', 'E-20 计量单位（8 种受控）'),
        ('category', 'VARCHAR', 'E-21 物料类别'),
        ('material_type', 'VARCHAR', 'STD/SELF/PURCHASE/ELECTRIC/SHEET/VIRTUAL'),
        ('brand', 'VARCHAR', '品牌'),
        ('status', 'VARCHAR', 'E-02'),
        ('substitute_code', 'VARCHAR', '替代料编码'),
        ('cost_price', 'DECIMAL', '成本价（元）'),
        ('safety_stock_min', 'DECIMAL', '安全库存下限'),
        ('safety_stock_max', 'DECIMAL', '安全库存上限'),
        ('abc_class', 'VARCHAR', 'E-05 ABC 分类'),
        ('is_controlled', 'TINYINT', '受控物料（安全件/关键件）'),
    ]),
    'md_code_map': ('mdm', 'MdCodeMap', False, [
        ('old_code', 'VARCHAR', '旧编码'),
        ('new_code', 'VARCHAR', '新编码'),
        ('map_type', 'VARCHAR', 'MERGE/REPLACE/ALIAS'),
        ('source', 'VARCHAR', '来源'),
        ('remark', 'VARCHAR', '备注'),
    ]),
    'md_supplier': ('mdm', 'MdSupplier', False, [
        ('supplier_code', 'VARCHAR', 'S{4位}'),
        ('supplier_name', 'VARCHAR', '供应商名称'),
        ('credit_code', 'VARCHAR', '统一社会信用代码'),
        ('level', 'VARCHAR', 'E-36 供应商分级'),
        ('category', 'VARCHAR', '主营品类'),
        ('contact', 'VARCHAR', '联系人'),
        ('phone', 'VARCHAR', '联系电话'),
        ('payment_terms', 'VARCHAR', '付款条件'),
        ('status', 'VARCHAR', '准入中/合格/暂停/黑名单'),
        ('blacklist_flag', 'TINYINT', '黑名单标记'),
    ]),
    'md_location': ('mdm', 'MdLocation', False, [
        ('location_code', 'VARCHAR', '{仓库}-{库区}-{排}-{列}-{层}'),
        ('warehouse_code', 'VARCHAR', '仓库码 YL/CP/QT/GC'),
        ('zone_code', 'VARCHAR', '库区码'),
        ('row_no', 'VARCHAR', '排'),
        ('col_no', 'VARCHAR', '列'),
        ('layer_no', 'VARCHAR', '层'),
        ('location_type', 'VARCHAR', '货位类型'),
        ('capacity', 'DECIMAL', '容量'),
        ('abc_class', 'VARCHAR', 'E-05'),
        ('color_flag', 'VARCHAR', '三色标识 RED/YELLOW/BLUE'),
        ('qr_code', 'VARCHAR', '二维码内容'),
        ('status', 'VARCHAR', 'E-02'),
    ]),
    # ---------------- T-CRM 营销 ----------------
    'crm_opportunity': ('crm', 'CrmOpportunity', True, [
        ('opp_no', 'VARCHAR', 'OPP-{YYYYMM}-{4位}'),
        ('opp_name', 'VARCHAR', '商机名称'),
        ('customer_id', 'BIGINT', '客户主体（md_customer）'),
        ('contact', 'VARCHAR', '联系人'),
        ('phone', 'VARCHAR', '联系电话'),
        ('summary', 'VARCHAR', '需求简述'),
        ('intention_level', 'VARCHAR', '意向等级 H/M/L'),
        ('expect_sign_date', 'DATE', '预计签约日'),
        ('expect_amount', 'DECIMAL', '预计金额（元）'),
        ('competitor', 'VARCHAR', '竞争对手'),
        ('owner_id', 'BIGINT', '负责人（默认创建人）'),
        ('status', 'VARCHAR', 'TRACKING/WON/LOST'),
        ('lost_reason', 'VARCHAR', '丢单原因'),
        ('converted_contract_id', 'BIGINT', '成交合同 id'),
    ]),
    'crm_opportunity_followup': ('crm', 'CrmOpportunityFollowup', False, [
        ('opportunity_id', 'BIGINT', '商机 id'),
        ('follow_type', 'VARCHAR', '跟进类型（拜访/电话/邮件/报价）'),
        ('content', 'VARCHAR', '跟进内容'),
        ('attach_ids', 'VARCHAR', '附件 id 集合'),
        ('next_action', 'VARCHAR', '下一步动作'),
        ('next_date', 'DATE', '下一步日期'),
        ('operator_id', 'BIGINT', '跟进人'),
    ]),
    'crm_customer_profile': ('crm', 'CrmCustomerProfile', False, [
        ('customer_id', 'BIGINT', '客户主体 id'),
        ('total_contract_amount', 'DECIMAL', '累计合同额'),
        ('total_receivable', 'DECIMAL', '累计应收'),
        ('total_received', 'DECIMAL', '累计回款'),
        ('complaint_count', 'INT', '投诉次数'),
        ('satisfaction_score', 'DECIMAL', '满意度'),
        ('last_txn_date', 'DATE', '最近交易日期'),
        ('rfm_r', 'INT', 'R 分（1-3）'),
        ('rfm_f', 'INT', 'F 分（1-3）'),
        ('rfm_m', 'INT', 'M 分（1-3）'),
        ('profile_json', 'JSON', '画像扩展'),
        ('snapshot_date', 'DATE', '快照日期'),
    ]),
    'crm_sales_funnel': ('crm', 'CrmSalesFunnel', False, [
        ('opportunity_id', 'BIGINT', '商机 id'),
        ('stage', 'VARCHAR', '阶段（OPPORTUNITY/QUOTE/CONTRACT/DELIVERY）'),
        ('enter_time', 'DATETIME', '进入时间'),
        ('exit_time', 'DATETIME', '离开时间'),
        ('stay_days', 'INT', '停留天数'),
        ('amount', 'DECIMAL', '金额'),
        ('probability', 'DECIMAL', '概率'),
    ]),
    # ---------------- T-CT 合同 ----------------
    'ct_contract': ('ct', 'CtContract', True, [
        ('contract_no', 'VARCHAR', '{主体}-{YYYY}-{性质}-{业务}-{4位}，不可改'),
        ('legacy_no', 'VARCHAR', '存量 CON-YYYYMM-nnnn'),
        ('contract_name', 'VARCHAR', '「对方主体－项目/事项」'),
        ('nature', 'VARCHAR', 'E-11 XS/CG/KJ'),
        ('party_a', 'VARCHAR', '甲方'),
        ('party_b', 'VARCHAR', '乙方'),
        ('legal_entity_id', 'BIGINT', '我方主体'),
        ('counterparty_id', 'BIGINT', '对方主体（md_customer）'),
        ('business_type', 'VARCHAR', 'E-12 业务类型'),
        ('amount', 'DECIMAL', '合同金额（元）'),
        ('currency', 'VARCHAR', '币种，默认 CNY'),
        ('tax_rate', 'DECIMAL', '税率 0-1'),
        ('payment_terms', 'VARCHAR', '付款条件原文'),
        ('delivery_addr', 'VARCHAR', '交货地址'),
        ('contact', 'VARCHAR', '联系人'),
        ('phone', 'VARCHAR', '联系电话'),
        ('delivery_date', 'DATE', '交货期'),
        ('warranty_months', 'INT', '质保月数'),
        ('acceptance_clause', 'VARCHAR', '验收条款'),
        ('sign_date', 'DATE', '签订日期'),
        ('effective_date', 'DATE', '生效日期'),
        ('perform_start', 'DATE', '履约起'),
        ('perform_end', 'DATE', '履约止'),
        ('copy_count', 'INT', '正本份数'),
        ('handler_id', 'BIGINT', '经办人'),
        ('reviewer_id', 'BIGINT', '评审人'),
        ('status', 'VARCHAR', 'E-13 合同状态'),
        ('filed_at', 'DATETIME', '建档完成时间（BR-02 判定）'),
        ('filing_deadline', 'DATE', 'sign_date + 3 工作日'),
        ('invoice_frozen', 'TINYINT', '开票冻结'),
        ('ai_extracted', 'TINYINT', '是否经 AI 提取'),
        ('archive_no', 'VARCHAR', '档号'),
        ('original_file_id', 'BIGINT', '签章 PDF（sys_attachment）'),
    ]),
    'ct_contract_payment_plan': ('ct', 'CtContractPaymentPlan', False, [
        ('contract_id', 'BIGINT', '合同 id'),
        ('phase_name', 'VARCHAR', '阶段名（预付款/到货款/验收款/质保金）'),
        ('percentage', 'DECIMAL', '比例 0-1'),
        ('amount', 'DECIMAL', '金额（元）'),
        ('plan_date', 'DATE', '计划收款日'),
        ('actual_amount', 'DECIMAL', '实际收款'),
        ('actual_date', 'DATE', '实际到账日'),
        ('status', 'VARCHAR', 'PENDING/PARTIAL/RECEIVED'),
        ('invoice_id', 'BIGINT', '关联发票'),
    ]),
    'ct_contract_risk': ('ct', 'CtContractRisk', False, [
        ('contract_id', 'BIGINT', '合同 id'),
        ('risk_type', 'VARCHAR', '风险类型'),
        ('risk_level', 'VARCHAR', 'H/M/L'),
        ('risk_desc', 'VARCHAR', '风险描述'),
        ('suggestion', 'VARCHAR', '建议'),
        ('is_adopted', 'TINYINT', '是否采纳'),
        ('ai_call_id', 'BIGINT', 'AI 调用 id（ai_call_log）'),
        ('reviewer_id', 'BIGINT', '复核人'),
    ]),
    'ct_contract_change': ('ct', 'CtContractChange', False, [
        ('change_no', 'VARCHAR', '{contract_no}-BC{n}'),
        ('contract_id', 'BIGINT', '合同 id'),
        ('change_type', 'VARCHAR', '变更类型'),
        ('change_reason', 'VARCHAR', '变更原因'),
        ('before_json', 'JSON', '变更前'),
        ('after_json', 'JSON', '变更后'),
        ('amount_delta', 'DECIMAL', '金额增减'),
        ('approval_status', 'VARCHAR', 'PENDING/APPROVED/REJECTED'),
        ('approved_by', 'BIGINT', '审批人'),
        ('effective_date', 'DATE', '生效日期'),
    ]),
    'ct_contract_dispatch': ('ct', 'CtContractDispatch', False, [
        ('contract_id', 'BIGINT', '合同 id'),
        ('receiver_id', 'BIGINT', '接收人（sys_user）'),
        ('dispatch_content', 'VARCHAR', '交底摘要'),
        ('sent_at', 'DATETIME', '发送时间'),
        ('read_at', 'DATETIME', '已阅时间'),
        ('remind_count', 'INT', '提醒次数'),
    ]),
    'ct_invoice': ('ct', 'CtInvoice', False, [
        ('invoice_no', 'VARCHAR', '发票号'),
        ('contract_id', 'BIGINT', '合同 id'),
        ('project_id', 'BIGINT', '项目 id'),
        ('invoice_type', 'VARCHAR', '发票类型'),
        ('amount', 'DECIMAL', '开票金额'),
        ('tax_rate', 'DECIMAL', '税率'),
        ('tax_amount', 'DECIMAL', '税额'),
        ('invoice_date', 'DATE', '开票日期'),
        ('status', 'VARCHAR', 'APPLIED/CONFIRMED/REJECTED'),
        ('t_plus_ref', 'VARCHAR', 'T+ 凭证号'),
        ('apply_by', 'BIGINT', '申请人'),
        ('confirm_by', 'BIGINT', '确认人'),
    ]),
    'ct_receipt': ('ct', 'CtReceipt', False, [
        ('receipt_no', 'VARCHAR', '回款单号'),
        ('contract_id', 'BIGINT', '合同 id'),
        ('project_id', 'BIGINT', '项目 id'),
        ('payment_plan_id', 'BIGINT', '收款计划行'),
        ('amount', 'DECIMAL', '回款金额'),
        ('receipt_date', 'DATE', '到账日期'),
        ('method', 'VARCHAR', '收款方式'),
        ('bank_ref', 'VARCHAR', '银行流水'),
        ('voucher_file_id', 'BIGINT', '回单附件'),
        ('status', 'VARCHAR', 'PENDING/CONFIRMED'),
        ('confirm_by', 'BIGINT', '财务确认人'),
    ]),
    # ---------------- T-AI 复核反馈 ----------------
    'ai_feedback': ('ct', 'AiFeedback', False, [
        ('call_id', 'BIGINT', 'AI 调用 id'),
        ('biz_type', 'VARCHAR', '业务对象类型'),
        ('biz_id', 'BIGINT', '业务对象 id'),
        ('ai_field', 'VARCHAR', '字段名'),
        ('ai_value', 'VARCHAR', 'AI 原值'),
        ('human_value', 'VARCHAR', '人工修正值'),
        ('is_correct', 'TINYINT', 'AI 是否正确'),
        ('feedback_note', 'VARCHAR', '备注'),
        ('operator_id', 'BIGINT', '操作人'),
    ]),
}


def gen_entity(table, module, entity, versioned, fields):
    pkg = {'mdm': 'mdm', 'crm': 'crm', 'ct': 'contract'}[module]
    base = 'VersionedEntity' if versioned else 'BaseEntity'
    imports = {
        'java.math.BigDecimal', 'java.time.LocalDate', 'java.time.LocalDateTime',
    }
    for _, t, _ in fields:
        jt = JAVA[t]
        if '.' in jt:
            imports.add(jt)
    lines = [
        f'package com.projlife.{pkg}.entity;',
        '',
        'import com.baomidou.mybatisplus.annotation.TableName;',
        'import com.projlife.common.entity.' + base + ';',
        '',
    ]
    lines += sorted(f'import {i};' for i in imports if f'import {i};' not in lines)
    lines += [
        '',
        '/**',
        f' * {"T-" + module.upper() if module != "ct" else "T-CT"} {table}（02 册字段字典，代码生成器产出，禁止手改字段）。',
        ' */',
        f'@TableName("{table}")',
        f'public class {entity} extends {base} {{',
        '',
    ]
    def camel(name):
        parts = name.split('_')
        return parts[0] + ''.join(p.capitalize() for p in parts[1:])

    for name, t, comment in fields:
        jt = JAVA[t]
        simple = jt.split('.')[-1]
        lines.append(f'    /** {comment} */')
        lines.append(f'    private {simple} {camel(name)};')
        lines.append('')
    for name, t, comment in fields:
        jt = JAVA[t].split('.')[-1]
        cap = camel(name)[0].upper() + camel(name)[1:]
        lines += [
            f'    public {jt} get{cap}() {{',
            f'        return {camel(name)};',
            '    }',
            '',
            f'    public void set{cap}({jt} {camel(name)}) {{',
            f'        this.{camel(name)} = {camel(name)};',
            '    }',
            '',
        ]
    lines.append('}')
    return '\n'.join(lines)


def gen_mapper(table, module, entity):
    pkg = {'mdm': 'mdm', 'crm': 'crm', 'ct': 'contract'}[module]
    return '\n'.join([
        f'package com.projlife.{pkg}.mapper;',
        '',
        'import com.baomidou.mybatisplus.core.mapper.BaseMapper;',
        f'import com.projlife.{pkg}.entity.{entity};',
        'import org.apache.ibatis.annotations.Mapper;',
        '',
        f'/** MyBatis-Plus Mapper：{entity} */',
        '@Mapper',
        f'public interface {entity}Mapper extends BaseMapper<{entity}> {{',
        '}',
    ])


def main():
    check = '--check' in sys.argv
    changed = 0
    for table, (module, entity, versioned, fields) in TABLES.items():
        base_dir = ROOT / MODULES[module]
        pkg = {'mdm': 'mdm', 'crm': 'crm', 'ct': 'contract'}[module]
        entity_path = base_dir / 'src' / 'main' / 'java' / 'com' / 'projlife' / pkg / 'entity' / f'{entity}.java'
        mapper_path = base_dir / 'src' / 'main' / 'java' / 'com' / 'projlife' / pkg / 'mapper' / f'{entity}Mapper.java'
        outputs = [(entity_path, gen_entity(table, module, entity, versioned, fields)),
                   (mapper_path, gen_mapper(table, module, entity))]
        for path, content in outputs:
            if check:
                current = path.read_text(encoding='utf-8') if path.exists() else ''
                if current != content:
                    print(f'[STALE] {path.relative_to(ROOT)}')
                    changed += 1
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding='utf-8', newline='\n')
    if check:
        print('CHECK:', 'UP-TO-DATE' if changed == 0 else f'{changed} files stale')
        sys.exit(1 if changed else 0)
    print(f'generated {len(TABLES)} entities + {len(TABLES)} mappers')


if __name__ == '__main__':
    main()
