import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Col, Drawer, Form, Input, Modal, Row, Select, Space, Tag, App, Statistic } from 'antd';
import { PlusOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { api, toastError } from '../api/client';
import { useAuth } from '../stores/auth';

interface Metric {
  metricCode: string;
  name: string;
  unit: string;
  target: string | null;
  direction: string;
  source: string;
  value: string | null;
  status: string;
}
interface CardItem {
  id: string;
  title: string;
  ownerId: string;
  dueDate: string;
  status: string;
  blockedReason: string | null;
  nextAction: string | null;
  escalated: boolean;
  crossFlag: string | null;
  sourceType: string | null;
}

const STATUS_TAG: Record<string, { color: string; label: string }> = {
  todo: { color: 'default', label: '待办' },
  doing: { color: 'blue', label: '进行中' },
  blocked: { color: 'red', label: '红灯' },
  done: { color: 'green', label: '已完成' },
};

function lightOf(m: Metric): 'green' | 'yellow' | 'red' | 'none' {
  if (m.value === null) return 'none';
  const t = Number(m.target);
  if (!Number.isFinite(t) || t === 0) return 'none';
  const v = Number(m.value);
  const ratio = m.direction === 'reverse' ? (v <= 0 ? 1 : t / v) : v / t;
  if (ratio >= 1) return 'green';
  if (ratio >= 0.8) return 'yellow';
  return 'red';
}

/** 07 册 P4 看板族：四区一栏（FR-M10-003）+ 板头指标条（FR-M10-004）+ 建卡/移动 */
export default function BoardView() {
  const { code = 'order' } = useParams();
  const nav = useNavigate();
  const { message } = App.useApp();
  const has = useAuth((s) => s.has);
  const [data, setData] = useState<{ board: { id: string; name: string; rhythm: string | null }; metrics: Metric[]; zones: { doing: CardItem[]; red: CardItem[]; closed: CardItem[] } } | null>(null);
  const [boards, setBoards] = useState<Array<{ boardCode: string; name: string }>>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [redOpen, setRedOpen] = useState<CardItem | null>(null);
  const [form] = Form.useForm();
  const [redForm] = Form.useForm();
  const [users, setUsers] = useState<Array<{ id: string; displayName: string }>>([]);
  const [meeting, setMeeting] = useState<{ id: string; redCards: Array<{ id: string; title: string }>; agenda: Array<{ name: string; from: number; to: number }> } | null>(null);
  const [ask, setAsk] = useState({ where: '', who: '', when: '' });
  const [minutes, setMinutes] = useState<{ redTotal: number; askedTotal: number; skippedTotal: number; items: Array<{ conclusion: string }> } | null>(null);

  const load = useCallback(async () => {
    try {
      const [view, bs] = await Promise.all([
        api<{ board: { id: string; name: string; rhythm: string | null }; metrics: Metric[]; zones: { doing: CardItem[]; red: CardItem[]; closed: CardItem[] } }>(
          'get',
          `/api/v1/kb/boards/${code}/view`,
        ),
        api<Array<{ boardCode: string; name: string }>>('get', '/api/v1/kb/boards'),
      ]);
      setData(view);
      setBoards(bs);
    } catch (e) {
      toastError(e);
    }
  }, [code]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = async () => {
    setCreateOpen(true);
    form.resetFields();
    try {
      const u = await api<{ list: Array<{ id: string; displayName: string }> }>('get', '/api/v1/sys/users?pageSize=200', undefined, undefined).catch(() => null);
      if (u) setUsers(u.list);
    } catch {
      setUsers([]);
    }
  };

  const createCard = async (status: 'todo' | 'blocked') => {
    const v = await form.validateFields();
    try {
      await api('post', '/api/v1/kb/cards', { ...v, boardId: data?.board.id, status, ...(status === 'blocked' ? { blockedReason: v.blockedReason, nextAction: v.nextAction } : {}) });
      message.success('建卡成功（五要素校验通过）');
      setCreateOpen(false);
      void load();
    } catch (e) {
      toastError(e);
    }
  };

  const move = async (card: CardItem, to: string) => {
    if (to === 'blocked') {
      setRedOpen(card);
      redForm.resetFields();
      return;
    }
    try {
      await api('post', `/api/v1/kb/cards/${card.id}/transition`, { to });
      void load();
    } catch (e) {
      toastError(e);
    }
  };

  const startMeeting = async () => {
    try {
      const m = await api<{ id: string; redCards: Array<{ id: string; title: string }>; agenda: Array<{ name: string; from: number; to: number }> }>(
        'post',
        `/api/v1/kb/boards/${data?.board.id}/meetings`,
        { attendees: [] },
      );
      setMeeting(m);
      setMinutes(null);
    } catch (e) {
      toastError(e);
    }
  };

  const submitAsk = async (skip: boolean) => {
    if (!meeting || !redOpenCard(meeting)) return;
    const card = meeting.redCards[0];
    try {
      await api('post', `/api/v1/kb/meetings/${meeting.id}/ask`, {
        cardId: card.id,
        skip,
        askWhere: ask.where || undefined,
        askWho: ask.who || undefined,
        askWhen: ask.when || undefined,
      });
      message.success(skip ? '已跳过（记"未闭环过堂"）' : '三问已记录');
      setAsk({ where: '', who: '', when: '' });
      setMeeting({ ...meeting, redCards: meeting.redCards.slice(1) });
    } catch (e) {
      toastError(e);
    }
  };

  function redOpenCard(m: { redCards: Array<{ id: string }> }) {
    return m.redCards.length > 0;
  }

  const finishMeeting = async () => {
    if (!meeting) return;
    try {
      const m = await api<{ redTotal: number; askedTotal: number; skippedTotal: number; items: Array<{ conclusion: string }> }>(
        'post',
        `/api/v1/kb/meetings/${meeting.id}/finish`,
        {},
      );
      setMinutes(m);
    } catch (e) {
      toastError(e);
    }
  };

  if (!data) return null;

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Card
        size="small"
        title={
          <Space>
            <Select
              size="small"
              style={{ width: 200 }}
              value={code}
              onChange={(v) => nav(`/kb/${v}`)}
              options={boards.map((b) => ({ value: b.boardCode, label: b.name }))}
            />
            <Tag>{data.board.rhythm ?? ''}</Tag>
          </Space>
        }
        extra={
          <Space>
            {has('kb:meeting:run') && (
              <Button icon={<ThunderboltOutlined />} type="primary" ghost onClick={() => void startMeeting()}>
                会议模式（30 分钟）
              </Button>
            )}
            {has('kb:card:create') && (
              <Button icon={<PlusOutlined />} onClick={() => void openCreate()}>
                建卡
              </Button>
            )}
          </Space>
        }
      >
        <Row gutter={12}>
          {data.metrics.map((m) => {
            const light = lightOf(m);
            const color = light === 'green' ? '#52c41a' : light === 'yellow' ? '#faad14' : light === 'red' ? '#ff4d4f' : '#d9d9d9';
            return (
              <Col key={m.metricCode} xs={12} md={6} lg={4}>
                <Statistic
                  title={
                    <span style={{ fontSize: 12 }}>
                      {m.name} {m.source === 'manual' && <Tag style={{ fontSize: 10 }}>手工</Tag>}
                    </span>
                  }
                  value={m.value === null ? '—' : m.unit === 'pct' ? `${(Number(m.value) * 100).toFixed(1)}%` : m.value}
                  valueStyle={{ color, fontSize: 22 }}
                  suffix={m.value === null ? '数据异常/无数据' : <span style={{ fontSize: 11, color: '#999' }}>目标 {m.target ?? '—'}</span>}
                />
              </Col>
            );
          })}
        </Row>
      </Card>

      <Row gutter={12}>
        <Col xs={24} md={8}>
          <Card size="small" title={`进行区（${data.zones.doing.length}）`}>
            {data.zones.doing.map((c) => (
              <CardBlock key={c.id} c={c} onMove={move} canMove={has('kb:card:move')} />
            ))}
            {data.zones.doing.length === 0 && <Empty text="无进行中卡片" />}
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card size="small" title={<span style={{ color: '#ff4d4f' }}>红灯区（{data.zones.red.length}）——早会唯一焦点</span>}>
            {data.zones.red.map((c) => (
              <CardBlock key={c.id} c={c} onMove={move} canMove={has('kb:card:move')} />
            ))}
            {data.zones.red.length === 0 && <Empty text="无红灯——保持！" />}
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card size="small" title={`闭环区（7 天内 ${data.zones.closed.length}）`}>
            {data.zones.closed.map((c) => (
              <CardBlock key={c.id} c={c} onMove={undefined} canMove={false} />
            ))}
            {data.zones.closed.length === 0 && <Empty text="7 天内无闭环卡片" />}
          </Card>
        </Col>
      </Row>

      <Drawer
        title="建卡（五要素 FR-M10-007）"
        width={440}
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        extra={
          <Space>
            <Button onClick={() => void createCard('todo')}>保存待办</Button>
            <Button danger onClick={() => void createCard('blocked')}>
              保存为红灯
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="事项（可验收，模糊词将被拦截）" rules={[{ required: true }]}>
            <Input.TextArea rows={2} placeholder="如：PJ2026-0001 发货单未生成" />
          </Form.Item>
          <Form.Item name="ownerId" label="责任人（唯一到人）" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={users.map((u) => ({ value: u.id, label: u.displayName }))} />
          </Form.Item>
          <Form.Item name="dueDate" label="承诺完成日（可上调不可下调）" rules={[{ required: true }]}>
            <Input placeholder="如 2026-12-01" />
          </Form.Item>
          <Form.Item name="blockedReason" label="卡点说明（红灯必填）">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="nextAction" label="下一步动作（红灯必填）">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="sourceType" label="关联来源（一键建卡）">
            <Select
              allowClear
              options={[
                { value: 'project', label: '项目' },
                { value: 'contract', label: '合同' },
                { value: 'work_order', label: '工单' },
                { value: 'ecn', label: 'ECN' },
              ]}
            />
          </Form.Item>
          <Form.Item name="sourceId" label="来源单据 ID">
            <Input />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title="拖入红灯区：卡点说明 + 下一步（必填 FR-M10-009）"
        open={!!redOpen}
        onOk={async () => {
          const v = await redForm.validateFields();
          if (!redOpen) return;
          try {
            await api('post', `/api/v1/kb/cards/${redOpen.id}/transition`, { to: 'blocked', ...v });
            message.success('已挂红灯');
            setRedOpen(null);
            void load();
          } catch (e) {
            toastError(e);
          }
        }}
        onCancel={() => setRedOpen(null)}
      >
        <Form form={redForm} layout="vertical">
          <Form.Item name="blockedReason" label="卡在哪" rules={[{ required: true }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="nextAction" label="下一步动作" rules={[{ required: true }]}>
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="会议模式（红灯过堂·三问 FR-M10-019）"
        open={!!meeting}
        footer={
          <Space>
            <Button onClick={() => setMeeting(null)}>挂起</Button>
            <Button type="primary" onClick={() => void finishMeeting()}>
              结束并生成纪要
            </Button>
          </Space>
        }
      >
        {minutes ? (
          <div>
            <p>红灯数：{minutes.redTotal}｜过堂：{minutes.askedTotal}｜未闭环跳过：{minutes.skippedTotal}</p>
            {minutes.items.map((i, idx) => (
              <p key={idx} style={{ fontSize: 12 }}>· {i.conclusion}</p>
            ))}
          </div>
        ) : meeting && meeting.redCards.length > 0 ? (
          <div style={{ textAlign: 'center', padding: 12 }}>
            <h2 style={{ color: '#ff4d4f' }}>{meeting.redCards[0].title}</h2>
            <p style={{ color: '#999' }}>剩余 {meeting.redCards.length} 张 · 每卡 2 分钟计时</p>
            <Input.TextArea
              rows={2}
              placeholder="一问：卡在哪？"
              value={ask.where}
              onChange={(e) => setAsk({ ...ask, where: e.target.value })}
              style={{ marginBottom: 8 }}
            />
            <Input placeholder="二问：谁解决？（唯一责任人）" value={ask.who} onChange={(e) => setAsk({ ...ask, who: e.target.value })} style={{ marginBottom: 8 }} />
            <Input placeholder="三问：何时解决？（承诺时间）" value={ask.when} onChange={(e) => setAsk({ ...ask, when: e.target.value })} style={{ marginBottom: 12 }} />
            <Space>
              <Button type="primary" onClick={() => void submitAsk(false)}>
                记录三问 → 下一张
              </Button>
              <Button onClick={() => void submitAsk(true)}>跳过（记未闭环过堂）</Button>
            </Space>
          </div>
        ) : (
          <p style={{ textAlign: 'center', padding: 24 }}>红灯已过完 —— 进入"今日承诺 / 上报协调"环节，点击"结束并生成纪要"。</p>
        )}
      </Modal>
    </Space>
  );
}

function CardBlock({ c, onMove, canMove }: { c: CardItem; onMove?: (c: CardItem, to: string) => void; canMove: boolean }) {
  const st = STATUS_TAG[c.status];
  return (
    <div
      style={{
        border: `1px solid ${c.status === 'blocked' ? '#ffccc7' : '#f0f0f0'}`,
        background: c.status === 'blocked' ? '#fff1f0' : '#fafafa',
        borderRadius: 8,
        padding: '8px 10px',
        marginBottom: 8,
      }}
    >
      <div style={{ fontSize: 13, fontWeight: 600 }}>
        {c.status === 'blocked' ? '🔴 ' : ''}
        {c.title}
        {c.escalated && <Tag color="volcano" style={{ marginLeft: 6 }}>🔥已升级</Tag>}
        {c.crossFlag === 'out' && <Tag style={{ marginLeft: 6 }}>已外挂</Tag>}
      </div>
      {c.blockedReason && <div style={{ fontSize: 12, color: '#cf1322' }}>卡点：{c.blockedReason}</div>}
      {c.nextAction && <div style={{ fontSize: 12, color: '#999' }}>下一步：{c.nextAction}</div>}
      <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>
        承诺 {new Date(c.dueDate).toLocaleDateString('zh-CN')}
        {new Date(c.dueDate).getTime() < Date.now() && c.status !== 'done' && <Tag color="red">逾期</Tag>}
      </div>
      {canMove && onMove && c.status !== 'done' && (
        <Space style={{ marginTop: 6 }}>
          {c.status !== 'doing' && (
            <Button size="small" onClick={() => onMove(c, 'doing')}>
              → 进行
            </Button>
          )}
          {c.status !== 'blocked' && (
            <Button size="small" danger onClick={() => onMove(c, 'blocked')}>
              → 红灯
            </Button>
          )}
          <Button size="small" type="primary" ghost onClick={() => onMove(c, 'done')}>
            → 闭环
          </Button>
        </Space>
      )}
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return <div style={{ color: '#bbb', textAlign: 'center', padding: 16, fontSize: 12 }}>{text}</div>;
}
