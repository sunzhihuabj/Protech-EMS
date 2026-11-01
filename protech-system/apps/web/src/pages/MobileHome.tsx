import { useEffect, useState } from 'react';
import { Badge, Card, Input, List, Tag } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { api } from '../api/client';
import { useAuth } from '../stores/auth';

interface MyCard {
  id: string;
  title: string;
  dueDate: string;
  status: string;
  overdue: boolean;
}

/** 07 册 §1.1 移动端 H5（FR-M10-028）：单列卡片流/指标横滑/红灯置顶/建卡三步 */
export default function MobileHome() {
  const me = useAuth((s) => s.me);
  const [cards, setCards] = useState<MyCard[]>([]);
  const [unread, setUnread] = useState(0);
  const [q, setQ] = useState('');
  const [results, setResults] = useState<Array<{ id: string; name: string }>>([]);

  useEffect(() => {
    api<MyCard[]>('get', '/api/v1/kb/my-cards').then(setCards).catch(() => undefined);
    api<{ unread: number }>('get', '/api/v1/sys/notifies').then((d) => setUnread(d.unread)).catch(() => undefined);
  }, []);

  const search = async (v: string) => {
    setQ(v);
    if (!v.trim()) return setResults([]);
    try {
      const d = await api<{ parties: Array<{ id: string; name: string }>; projects: Array<{ id: string; standardName: string }> }>(
        'get',
        `/api/v1/mdm/search?q=${encodeURIComponent(v)}`,
      );
      setResults([
        ...d.projects.map((p) => ({ id: p.id, name: `项目：${p.standardName}` })),
        ...d.parties.map((p) => ({ id: p.id, name: `主体：${p.name}` })),
      ]);
    } catch {
      setResults([]);
    }
  };

  const red = cards.filter((c) => c.status === 'blocked');
  const other = cards.filter((c) => c.status !== 'blocked');

  return (
    <div style={{ padding: 12, maxWidth: 480, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <b>Protech 移动端</b>
        <Badge count={unread} size="small">
          <span style={{ fontSize: 12 }}>通知</span>
        </Badge>
      </div>
      <Input
        prefix={<SearchOutlined />}
        placeholder="全局搜索（任意叫法命中）"
        value={q}
        onChange={(e) => void search(e.target.value)}
        style={{ marginBottom: 12 }}
      />
      {results.length > 0 && (
        <Card size="small" style={{ marginBottom: 12 }}>
          {results.map((r) => (
            <div key={r.id} style={{ fontSize: 13, padding: '4px 0' }}>
              {r.name}
            </div>
          ))}
        </Card>
      )}

      <div style={{ marginBottom: 8, color: '#999', fontSize: 12 }}>
        {me?.user.displayName} · 我的卡（红灯置顶 FR-M10-028）
      </div>

      {red.map((c) => (
        <Card key={c.id} size="small" style={{ marginBottom: 8, background: '#fff1f0', border: '1px solid #ffccc7' }}>
          <div style={{ fontSize: 14, fontWeight: 600 }}>🔴 {c.title}</div>
          <div style={{ fontSize: 12, color: '#999' }}>承诺 {new Date(c.dueDate).toLocaleDateString('zh-CN')}</div>
        </Card>
      ))}
      {other.map((c) => (
        <Card key={c.id} size="small" style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 14 }}>
            {c.title} {c.overdue && <Tag color="red">逾期</Tag>}
          </div>
          <div style={{ fontSize: 12, color: '#999' }}>
            承诺 {new Date(c.dueDate).toLocaleDateString('zh-CN')} · {c.status === 'doing' ? '进行中' : '待办'}
          </div>
        </Card>
      ))}
      {cards.length === 0 && (
        <Card size="small">
          <span style={{ color: '#999', fontSize: 13 }}>暂无我的卡——红灯卡与今日承诺将在此展示</span>
        </Card>
      )}

      <div style={{ textAlign: 'center', color: '#bbb', fontSize: 11, marginTop: 16 }}>
        建卡三步：选板 → 事项 → 人+日期（P4 完整版见 PC 端）
      </div>
      <List dataSource={[]} renderItem={() => <List.Item />} />
    </div>
  );
}
