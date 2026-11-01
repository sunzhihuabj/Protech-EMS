import { useEffect, useState } from 'react';
import { Alert, Button, Card, Input, Select, Space, Table, Tag, App } from 'antd';
import { api, toastError } from '../api/client';

interface Capability {
  code: string;
  name: string;
  level: string;
  enabled: boolean;
}

/** 07 册 /ai AI 助手：能力点运行 + Ask-first 采纳提示 */
export default function AiAssistant() {
  const { message } = App.useApp();
  const [caps, setCaps] = useState<Capability[]>([]);
  const [selected, setSelected] = useState('AI-06');
  const [input, setInput] = useState('');
  const [result, setResult] = useState<{ capability: string; askFirst?: boolean; answer?: string; source?: string; suggestion?: string; draft?: string; interpretation?: string; minutes?: { redTotal: number; actionItems: Array<{ owner: string; deadline: string; task: string }> }; risk?: string } | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api<Capability[]>('get', '/api/v1/ai/capabilities').then(setCaps).catch(toastError);
  }, []);

  const run = async () => {
    setLoading(true);
    setResult(null);
    try {
      const body: Record<string, unknown> = {};
      if (selected === 'AI-04') body.title = input;
      if (selected === 'AI-06') body.question = input;
      if (selected === 'AI-07') body.kind = input || 'redlight_cluster';
      if (selected === 'AI-11') body.reportKey = input || 'contracts';
      const r = await api<Record<string, unknown>>('post', `/api/v1/ai/${selected}/run`, body);
      setResult(r as typeof result);
    } catch (e) {
      toastError(e);
    } finally {
      setLoading(false);
    }
  };

  const textOf = (r: NonNullable<typeof result>) =>
    r.answer ?? r.suggestion ?? r.draft ?? r.interpretation ?? r.risk ?? JSON.stringify(r.minutes ?? '');

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Card size="small" title="AI 助手（Ask-first · 白名单 · 全调用审计 FR-M16-014）">
        <Space.Compact style={{ width: '100%' }}>
          <Select
            style={{ width: 260 }}
            value={selected}
            onChange={setSelected}
            options={caps
              .filter((c) => c.enabled)
              .map((c) => ({ value: c.code, label: `${c.code} ${c.name}（${c.level === 'auto' ? '直执行' : '先确认'}）` }))}
          />
          {(selected === 'AI-04' || selected === 'AI-06' || selected === 'AI-07' || selected === 'AI-11') && (
            <Input
              style={{ flex: 1 }}
              placeholder={
                selected === 'AI-06'
                  ? '问：现在红灯有多少 / 项目 / 库存 / 回款（白名单口径）'
                  : selected === 'AI-07'
                    ? 'redlight_cluster 或 kit_short'
                    : selected === 'AI-11'
                      ? 'contracts 或 stock-moves'
                      : '输入模糊卡点原文'
              }
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onPressEnter={() => void run()}
            />
          )}
          <Button type="primary" loading={loading} disabled={!['AI-04', 'AI-06', 'AI-07', 'AI-11'].includes(selected)} onClick={() => void run()}>
            运行
          </Button>
        </Space.Compact>
      </Card>

      {result && (
        <Card size="small" title={<Tag color="blue">{result.capability}</Tag>}>
          {result.askFirst && (
            <Alert type="info" showIcon style={{ marginBottom: 8 }} message="Ask-first：AI 结果为草稿，采纳前请人工确认（FR-M16-014）" />
          )}
          <pre style={{ whiteSpace: 'pre-wrap', margin: 0, fontSize: 13 }}>{textOf(result)}</pre>
          {result.source && <Tag style={{ marginTop: 8 }}>口径来源：{result.source}</Tag>}
        </Card>
      )}

      <Card size="small" title="能力点注册表（12 点）">
        <Table<Capability>
          rowKey="code"
          size="small"
          pagination={false}
          dataSource={caps}
          columns={[
            { title: '编号', dataIndex: 'code', width: 80 },
            { title: '名称', dataIndex: 'name' },
            { title: '分级', dataIndex: 'level', width: 90, render: (v: string) => <Tag>{v === 'auto' ? '直执行' : v === 'ask' ? '先确认' : '禁止'}</Tag> },
            {
              title: '状态',
              dataIndex: 'enabled',
              width: 100,
              render: (v: boolean) => (v ? <Tag color="green">已开放</Tag> : <Tag>路线中</Tag>),
            },
          ]}
        />
      </Card>
    </Space>
  );
}
