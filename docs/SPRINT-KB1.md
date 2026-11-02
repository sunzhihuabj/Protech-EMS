# SPRINT-KB1：会议纪要落库闭环（FR-KB-11 深度）

> 日期：2026-11-03 · 分支：`feature/dev`（projlife-dev）· 前置：kb_meeting 表（V12）+ POST /kb/meetings（S24 基座）
> 摸底结论：基座已有但三处真实差距——逐卡结论未落 promisesJson（skipCurrent 仅弹提示）、boardId 硬编码 1、reviewedCount 口径错误（=redCount）；且 meetingPdf 存在两个**从未被测试暴露的存量缺陷**。

## 一、实施内容

### 后端（module-cost）
| 项 | 内容 |
|---|---|
| save() 校验 | promisesJson 必须为合法 JSON 数组（A-30：MySQL JSON 列拒收非法串），非法 → 1001；minutes>2000/attendees>500 截断防 500 |
| detail() 增强 | +items 键：promisesJson 解析还原逐卡结论 [{title,where,who,when,result:CLOSED\|SKIPPED}]；H2 JSON 包裹解包容错 |
| boards() | GET /kb/boards 看板清单（boardCode→id 解析数据源，kb_board 种子 KB01-03） |
| GET /kb/meetings/{id} | 纪要详情端点（纪要页服务端数据源） |
| meetingPdf() | 修复两个存量缺陷：①实体强转 Map 的 ClassCastException（原代码从未跑通）②中文在 HELVETICA 必抛（CJK 字体链 simhei/msyh/simsun/wqy/noto + 无字体 ASCII 降级）；+逐卡结论行 |

### 前端（projlife-web）
| 文件 | 内容 |
|---|---|
| Board.vue | outcomes 逐卡收集（resolveCurrent→CLOSED / skipCurrent→SKIPPED），随三问 30s 自动暂存与恢复；endMeeting 落 promisesJson+真实 boardId（/kb/boards 映射）+准确计数（reviewed=outcomes.length, closed=闭环数）+未闭环标题进 minutes；成功后清暂存 |
| MeetingMinutes.vue | 改服务端数据源（GET /kb/meetings/{id}）：meeting 字段 + items 逐卡表（PtStatusTag CLOSED/SKIPPED）+ minutesText；+PDF 下载按钮（沿用 Users.vue blob 模式）；去除 sessionStorage 依赖 |

## 二、验证结果

- **E2E 6/6**（S27KbMeetingTest，H2）：落库回显计数 / detail 还原 items 3 条（CLOSED/SKIPPED 字段逐一断言）/ 非法 promisesJson→1001 / boards 种子清单 / service 直生 PDF（防实体强转回归）/ PDF 导出 >500 字节且 %PDF 魔数
- **前端 npm run build 通过**（5.75s）
- **全量回归**：全反应堆 BUILD SUCCESS（103+6 E2E + 24 集成 + 3 同步 + 既有全量）

## 三、测试暴露并修复的缺陷（测试先行价值）

1. **meetingPdf 实体强转 Map**（存量，未跑通过）：`(Map) detail.get("meeting")` 但 detail 放的是实体 → 每次调用必 9001。改实体 getter。
2. **PDF 中文必抛**（存量）：HELVETICA 无法编码中文 → CJK 字体链 + ASCII 降级双保险。
3. **Long→String 全局序列化**（已知坑重演）：E2E 断言 id 用 Number 接收失败 → String.valueOf 转换。

## 四、遗留

- PDF 中文需目标机器存在任一候选字体（Win 自带 simhei/msyh；Linux 部署需安装 wqy/noto，已列入候选路径）——打包内置字体随部署批次评估
- job 定时同步目录/纪要归档随部署批次
