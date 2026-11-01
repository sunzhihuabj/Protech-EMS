import 'dotenv/config';
import { Worker, Queue } from 'bullmq';
import axios from 'axios';

/**
 * Worker（11 册 §二）：一切定时与异步任务。
 * 队列：Redis 7 / Memurai（本机 6379）；任务体 = 调 api 内部扫描端点（逻辑单一来源，内部令牌保护）。
 *  - redlight-scan：每 30 分钟（BR-07 红灯 3 天升级 / FR-M10-014）
 *  - no-response-scan：每 30 分钟（48h 未响应升级周会 FR-M10-015）
 *  - metric-snapshot：每日 06:00 全量 + 每 2h 增量（FR-M10-004）
 *  - meeting-remind：每日 07:30（FR-M10-022 未开会提醒）
 */
const API = process.env.API_BASE ?? 'http://localhost:9090';
const TOKEN = process.env.INTERNAL_TOKEN ?? 'internal-dev-token';
const REDIS = process.env.REDIS_URL ?? 'redis://localhost:6379';

async function call(path: string): Promise<unknown> {
  const res = await axios.post(`${API}/api/v1/kb/${path}`, { token: TOKEN }, { timeout: 20000 });
  if (res.data.code !== 0) throw new Error(`${path}: ${res.data.msg}`);
  return res.data.data;
}

const queue = new Queue('protech-scans', { connection: { url: REDIS } });

async function schedule() {
  await queue.add(
    'redlight-scan',
    {},
    { repeat: { pattern: '*/30 * * * *' }, jobId: 'redlight-scan', removeOnComplete: 50 },
  );
  await queue.add(
    'no-response-scan',
    {},
    { repeat: { pattern: '*/30 * * * *' }, jobId: 'no-response-scan', removeOnComplete: 50 },
  );
  await queue.add(
    'metric-snapshot',
    {},
    { repeat: { pattern: '0 */2 * * *' }, jobId: 'metric-snapshot', removeOnComplete: 50 },
  );
  await queue.add(
    'metric-daily',
    {},
    { repeat: { pattern: '0 6 * * *' }, jobId: 'metric-daily', removeOnComplete: 50 },
  );
  await queue.add(
    'meeting-remind',
    {},
    { repeat: { pattern: '30 7 * * 1-5' }, jobId: 'meeting-remind', removeOnComplete: 50 },
  );
  await queue.add(
    'tplus-push',
    {},
    { repeat: { pattern: '*/30 * * * *' }, jobId: 'tplus-push', removeOnComplete: 50 },
  );
  await queue.add(
    'inventory-snapshot',
    {},
    { repeat: { pattern: '30 23 * * *' }, jobId: 'inventory-snapshot', removeOnComplete: 50 },
  );
  await queue.add(
    'receipt-remind',
    {},
    { repeat: { pattern: '0 8 * * 1-5' }, jobId: 'receipt-remind', removeOnComplete: 50 },
  );
  await queue.add(
    'slowmoving-scan',
    {},
    { repeat: { pattern: '0 3 * * *' }, jobId: 'slowmoving-scan', removeOnComplete: 50 },
  );
  await queue.add(
    'as-inspections',
    {},
    { repeat: { pattern: '0 4 * * *' }, jobId: 'as-inspections', removeOnComplete: 50 },
  );
  await queue.add(
    'warranty-watch',
    {},
    { repeat: { pattern: '0 4 * * *' }, jobId: 'warranty-watch', removeOnComplete: 50 },
  );
  await queue.add(
    'archive-remind',
    {},
    { repeat: { pattern: '0 5 * * *' }, jobId: 'archive-remind', removeOnComplete: 50 },
  );
  await queue.add(
    'report-push',
    {},
    { repeat: { pattern: '0 8 * * 1' }, jobId: 'report-push', removeOnComplete: 50 },
  );
  console.log('[worker] 定时任务已注册（30m 扫描×2/T+推送 / 2h 指标 / 06:00 全量 / 07:30 未开会 / 08:00 回款提醒 / 23:30 快照）');
}

const TASKS: Record<string, () => Promise<unknown>> = {
  'redlight-scan': () => call('internal/scans/redlight'),
  'no-response-scan': () => call('internal/scans/no-response'),
  'metric-snapshot': () => call('internal/metrics/compute'),
  'metric-daily': () => call('internal/metrics/compute'),
  'meeting-remind': () => call('internal/scans/meeting-remind'),
  'tplus-push': () => call('internal/tplus/push'),
  'inventory-snapshot': () => call('internal/inventory/snapshot'),
  'receipt-remind': () => call('internal/finance/remind-due'),
  'slowmoving-scan': () => call('internal/slowmoving/scan'),
  'as-inspections': () => call('internal/aftersales/generate-inspections'),
  'warranty-watch': () => call('internal/aftersales/warranty-watch'),
  'archive-remind': () => call('internal/archives/remind-scan'),
  'report-push': () => call('internal/push'),
};

async function run() {
  await schedule();
  const worker = new Worker(
    'protech-scans',
    async (job) => {
      const task = TASKS[job.name];
      if (!task) return;
      const started = Date.now();
      try {
        const result = await task();
        console.log(`[worker] ${job.name} ok (${Date.now() - started}ms)`, JSON.stringify(result));
      } catch (e) {
        console.error(`[worker] ${job.name} failed:`, (e as Error).message);
        throw e;
      }
    },
    { connection: { url: REDIS }, concurrency: 2 },
  );
  worker.on('failed', (job, err) => console.error(`[worker] job ${job?.name} 重试失败: ${err.message}`));
  console.log(`[worker] 运行中 → api=${API} redis=${REDIS}`);
}

void run();
