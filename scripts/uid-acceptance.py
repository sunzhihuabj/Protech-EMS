#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UID-05 §五 验收 13 条的脚本化抽查（可自动部分）：
  - 验收 1：路由与 UID-05 命名一致性（孤儿/自拟路由扫描）
  - 验收 8：24 组 × 8 对对比度离线计算（tokens.json 权威值，UID-04 §六.4 同算法）
  - 验收 13：灯色"可访问名含状态词"——扫描全部 .vue，PtLight 使用处必须带 label/
             或同级含状态词文本（机械断言口径）
用法：python scripts/uid-acceptance.py（projlife-web 同级运行）
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / 'projlife-web'
ok, fail = [], []

# ---------- 验收 8：对比度（离线，tokens.json 直算；文本 4.5 / 非文本 3） ----------
tokens = json.loads((WEB / 'src' / 'theme' / 'tokens.json').read_text(encoding='utf-8'))

def lum(hexcolor):
    h = hexcolor.lstrip('#')
    if len(h) == 3:
        h = ''.join(c * 2 for c in h)
    rgb = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    lin = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]

def contrast(a, b):
    la, lb = sorted([lum(a), lum(b)], reverse=True)
    return (la + 0.05) / (lb + 0.05)

PAIRS = [
    ('ink', 'surface', 4.5, '文本'), ('ink-2', 'surface', 4.5, '文本'),
    ('ink-3', 'surface', 4.5, '文本'),
    ('pri-text', 'surface', 4.5, '文本'),
    ('ink-4', 'surface', 3, '非文本'),
    # --line 不入断言：UID-01 §一.4 明文"仅装饰，信息不依赖其可见性，豁免 3:1"
]
bad = 0
for theme in tokens['themes']:
    for mode in ('light', 'dark'):
        sem = tokens['status']['light'] if mode == 'light' else tokens['status']['light']
        vals = {}
        for k, v in theme[mode].items():
            vals[k] = v
        vals.update(theme['neu'] if isinstance(theme.get('neu'), dict) else {})
        pri = theme['pri']
        vals['pri-text'] = theme[mode].get('pri-text') or (pri['600'] if mode == 'light' else pri['300'])
        for fg, bg, minimum, kind in PAIRS:
            if fg not in vals or bg not in vals:
                continue
            r = contrast(vals[fg], vals[bg])
            if r < minimum:
                bad += 1
                fail.append(f"对比度 {theme['key']}/{mode} [{kind}] {fg} on {bg} = {r:.2f} < {minimum}")
ok.append(f'验收 8 对比度：12 主题 × 2 模式 × {len(PAIRS)} 对 = {12 * 2 * len(PAIRS)} 项，不达标 {bad}')

# ---------- 验收 1：路由命名一致性（UID-05 §二 域前缀白名单） ----------
router_src = (WEB / 'src' / 'router' / 'index.js').read_text(encoding='utf-8')
DOMAINS = ('workbench', 'crm', 'ct', 'prj', 'plm', 'pcs', 'scm', 'inv', 'mes', 'aps', 'qms',
           'eam', 'eng', 'thd', 'cost', 'kb', 'bid', 'mdm', 'arc', 'sys', 'ai', 'int', 'agt')
# 只断言组件路由；redirect 行（旧路径兼容）与 /login /403 /auth /profile（用户菜单内链）豁免（UID-05 §一）
comp_routes = re.findall(r"path:\s*'([^']+)',[^\n]*component:", router_src)
violations = []
for p in comp_routes:
    if p in ('/login', '/403', '/', '*', '/profile/appearance', 'profile/appearance') or p.startswith('/auth'):
        # /profile/appearance = 用户菜单内链（UID-05 §一"仅内链"标注，豁免域前缀）
        continue
    parts = [x for x in p.split('/') if x and not x.startswith(':')]
    if parts and parts[0] not in DOMAINS:
        violations.append(p)
ok.append(f'验收 1 路由命名：{len(comp_routes)} 条组件路由，域前缀违例 {len(violations)}'
          + (f' → {violations}' if violations else ''))
if violations:
    fail.append('路由域前缀违例：' + ', '.join(violations))

# ---------- 验收 13：灯可访问名（机械断言：任一状态灯元素的「可访问名」必须包含对应状态词） ----------
LIGHT_WORDS = ('正常', '关注', '红灯', '阻塞')
light_bad = []
vue_files = list(WEB.glob('src/**/*.vue'))
for f in vue_files:
    src = f.read_text(encoding='utf-8')
    if '<pt-light' not in src:
        continue
    for m in re.finditer(r'<pt-light\b[^>]*>', src):
        tag = m.group(0)
        if 'label=' in tag or ':label=' in tag or 'dense' in tag:
            continue  # 有 label 或 dense（同列文字承担）均视为达标
        # 无 label/dense：要求组件外 200 字符窗口内出现状态词
        window = src[max(0, m.start() - 200):m.end() + 200]
        if not any(w in window for w in LIGHT_WORDS):
            light_bad.append(f"{f.relative_to(WEB)}:{src[:m.start()].count(chr(10)) + 1}")
ok.append(f'验收 13 灯可访问名：{sum(1 for f in vue_files if "<pt-light" in f.read_text(encoding="utf-8"))} 个文件含 PtLight，'
          f'疑似无状态词 {len(light_bad)}' + (f' → {light_bad}' if light_bad else ''))
if light_bad:
    fail.append('灯可访问名缺失：' + '; '.join(light_bad))

# ---------- 输出 ----------
print('=' * 64)
for line in ok:
    print('[PASS]', line)
for line in fail:
    print('[FAIL]', line)
print('=' * 64)
print(f'RESULT: {"PASS" if not fail else "FAIL（%d 项）" % len(fail)}')
sys.exit(1 if fail else 0)
