# SPRINT UID-R3 · Pt 组件套件与九板页（UID-03/04 落地第一批）

> 日期：2026-10-31 ｜ 状态：✅ 第一批完成（build 绿 + 暗态九板视觉确认）

## 一、交付

| 件 | 内容 | UID 依据 |
|---|---|---|
| PtLight | 四编码（●▲■◯ + 前缀字 正常/关注/红灯/阻塞 + aria）+ 尺寸阶梯 table8/card10/metric12（无任意像素 prop） | UID-01 §七.2、UID-05 验收 13 |
| PtStatusTag | 三语法（流转=描边/语义=浅底/中性=灰底）；高 22/--r-1/12px；禁 effect=dark | UID-01 §七.2 |
| PtMetric | 高 96 三行；分母 0 显示"—"（--ink-3 + aria）禁 0%；手工角标；整卡可点下钻 | UID-01 §七.3 |
| PtBoardCard | 五要素三行固定；缺项两级叠加（字段级 danger-bg+dashed / 卡级 dashed+待补全 Tag）；红灯升级三通道（描边/中性 Tag/滞留文字）；逾期 N 天 | UID-03 §2.2(c)(d) |
| PtBlockAlert | 422 三段式：blockCode 徽标(12 mono)+原因+直达链接；sessionStorage 快照 keepData | UID-03 §2.4(d)、V4-01 §6.2 |
| Board.vue（P3 九板页） | 四区固定顺序 **指标→红灯→进行→闭环**（R-03）；红灯区 danger-bg+左竖条置顶；闭环默认折叠 48；板头 56；**会议模式**（+2 档字号/议程条四段/计时器到点变色/进入下一段）与 **大屏**（?mode=wall） | UID-03 §2.2(a)—(f) |
| 路由 | kbBoard → Board.vue；旧 Kanban 保留为卡片台账 | UID-05 §二⑦ |

## 二、验证
- vite build ✓；暗态截图：四区渲染/红灯区视觉权重/水印 --wm/侧栏令牌全对 ✓

## 三、与 RPD 对应
FR-KB-01~14 呈现层（四区/五要素/升级三通道/指标卡）；FR-KB-09/10/11 会议模式骨架；NFR-18（灯旁必有字/aria 可访问名）；A-06/A-07 空值与脱敏口径。

## 四、待续（R3 第二批，随下一轮）
- 12 页面逐页迁 PtTable/PtFilterBar（P4 四段范式 + 空态三型 + 已选 Tag 行）
- 会议模式三问录入区（FR-KB-10 强制录入）与结束纪要
- /workbench/todo、/workbench/my-task、/qms/inspection 等模板页批量生成
- 审计打印强制 t1 亮色（UID-01 §八.9）
