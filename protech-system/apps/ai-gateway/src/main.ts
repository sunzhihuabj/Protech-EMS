import 'dotenv/config';
import express from 'express';

/**
 * AI 网关（11 册独立进程；10 册双通道路由）。
 * 通道：AI_PROVIDER=mock（默认，确定性）/ deepseek / qwen（HTTP API，需 AI_API_KEY）。
 * 职责：能力点白名单校验 + 脱敏（人名→角色占位）+ 转发 + 调用审计回执。
 * api 服务通过 AI_PROVIDER=http + AI_GATEWAY_URL 走本进程；直连 mock 时本进程可离线。
 */
const app = express();
app.use(express.json({ limit: '2mb' }));

const CAPABILITIES: Record<string, { name: string; level: 'auto' | 'ask' }> = {
  'AI-01': { name: '合同要素提取', level: 'ask' },
  'AI-02': { name: '主数据查重辅助', level: 'auto' },
  'AI-03': { name: '台账名称归一', level: 'auto' },
  'AI-04': { name: '模糊卡点改写', level: 'ask' },
  'AI-05': { name: '会议纪要生成', level: 'ask' },
  'AI-06': { name: '数据问答 ChatBI', level: 'ask' },
  'AI-07': { name: '异常预警解读', level: 'ask' },
  'AI-08': { name: '报价辅助', level: 'ask' },
  'AI-09': { name: '知识库问答 RAG', level: 'ask' },
  'AI-10': { name: '日报周报生成', level: 'ask' },
  'AI-11': { name: '报表解读', level: 'auto' },
  'AI-12': { name: '智能填单', level: 'ask' },
};

/** 脱敏：人名/手机号 → 占位（10 册 §7 数据最小化，敏感场景切本地 Ollama 则零出网） */
function desensitize(text: string): string {
  return text
    .replace(/1[3-9]\d{9}/g, '{{手机号}}')
    .replace(/[\u4e00-\u9fa5]{1,2}(先生|女士|经理|总|工)/g, '{{称谓}}');
}

app.get('/ai/v1/health', (_req, res) => {
  res.json({ status: 'up', provider: process.env.AI_PROVIDER ?? 'mock', capabilities: Object.keys(CAPABILITIES).length });
});

app.get('/ai/v1/capabilities', (_req, res) => {
  res.json({ code: 0, data: Object.entries(CAPABILITIES).map(([code, def]) => ({ code, ...def })) });
});

/** 能力点统一入口：白名单外 403（AGT-* 边界码，11 册 §4.1） */
app.post('/ai/v1/capabilities/:cap/run', async (req, res) => {
  const cap = req.params.cap;
  if (!CAPABILITIES[cap]) {
    return res.status(403).json({ code: 4003, msg: `能力点不在白名单：${cap}`, blockCode: 'AGT-WHITELIST' });
  }
  const raw = JSON.stringify(req.body ?? {});
  const clean = desensitize(raw);
  const provider = process.env.AI_PROVIDER ?? 'mock';

  if (provider === 'mock') {
    // 确定性 mock：echo 输入摘要 + 模板结果（api 内联通道行为一致）
    return res.json({
      code: 0,
      data: {
        capability: cap,
        provider,
        desensitized: clean !== raw,
        result: { note: `mock 通道（${cap} ${CAPABILITIES[cap].name}）`, inputDigest: clean.slice(0, 120) },
        askFirst: CAPABILITIES[cap].level === 'ask',
      },
    });
  }

  // DeepSeek/Qwen OpenAI 兼容通道（AI_API_KEY 必配；失败返回 AGT-PROVIDER 边界码）
  const apiKey = process.env.AI_API_KEY;
  if (!apiKey) {
    return res.status(503).json({ code: 4002, msg: 'AI_API_KEY 未配置', blockCode: 'AGT-PROVIDER' });
  }
  const baseUrl = provider === 'qwen' ? 'https://dashscope.aliyuncs.com/compatible-mode/v1' : 'https://api.deepseek.com/v1';
  const model = provider === 'qwen' ? 'qwen-plus' : 'deepseek-chat';
  try {
    const upstream = await fetch(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({
        model,
        messages: [
          { role: 'system', content: '你是 Protech 企业管理系统的 AI 助手。输出必须可溯源，不确定时明示。' },
          { role: 'user', content: `[${cap}] ${clean}` },
        ],
      }),
      signal: AbortSignal.timeout(30000),
    });
    const data = (await upstream.json()) as { choices?: Array<{ message?: { content?: string } }> };
    const content = data.choices?.[0]?.message?.content ?? '';
    res.json({ code: 0, data: { capability: cap, provider, result: { content }, askFirst: CAPABILITIES[cap].level === 'ask' } });
  } catch (e) {
    res.status(502).json({ code: 4001, msg: `上游模型调用失败：${(e as Error).message}`, blockCode: 'AGT-PROVIDER' });
  }
});

const port = Number(process.env.AI_GATEWAY_PORT ?? 8090);
app.listen(port, () => {
  console.log(`[ai-gateway] listening on :${port} (provider=${process.env.AI_PROVIDER ?? 'mock'}, capabilities=${Object.keys(CAPABILITIES).length})`);
});
