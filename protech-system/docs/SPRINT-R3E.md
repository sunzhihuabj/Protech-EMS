# SPRINT R3-E · 独立 AI 网关进程 + 移动端 H5 + 操作手册

> R3 冲刺收尾｜分支 protech-v2｜2026-11-02

## 交付内容
- **apps/ai-gateway 独立进程**（11 册架构图第六容器）：Express :8090；12 能力点白名单；脱敏（手机号/称谓→占位，10 册 §7 数据最小化）；三通道路由（mock/deepseek/qwen，OpenAI 兼容）；AGT-WHITELIST/AGT-PROVIDER 边界码；compose 容器位补齐（api 可经 AI_PROVIDER=http+AI_GATEWAY_URL 走网关，默认内联 mock 恒可用）
- **移动端 H5**（FR-M10-028）：/m 单列卡片流（红灯置顶）+ 全局搜索（别名命中）+ 通知角标 + 建卡三步提示
- **操作手册**（FR-M00-012）：/manual 角色化折叠指南（铁律/六角色场景），头部入口
- deploy/compose 六容器→七容器（新增 ai-gateway）

## 验证
- gateway /ai/v1/health 200 mock 12 能力点
- web build ✓
