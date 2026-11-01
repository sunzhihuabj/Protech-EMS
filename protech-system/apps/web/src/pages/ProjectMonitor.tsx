import { useEffect, useState } from 'react';
import { Card, Col, Descriptions, Row, Space, Statistic, Table, Tag, Timeline } from 'antd';
import { useParams } from 'react-router-dom';
import { api, toastError } from '../api/client';

interface ProjectMonitorData {
  project: {
    projectCode: string;
    standardName: string;
    status: string;
    unitCount: number;
    contractAmountCents: string | null;
    warrantyStart: string | null;
    warrantyEnd: string | null;
    pendingFlags: string[];
  };
  timeline: Array<{ stage: string; state: string }>;
  keyNodes: { contractAmountCents: string | null; receiptCents: string; kitShort: number; milestoneOverdue: number };
  milestones: Array<{ id: string; name: string; planDate: string | null; actualDate: string | null; status: string }>;
  workOrders: Array<{ id: string; woCode: string; status: string; kitStatus: string | null }>;
  dispatches: Array<{ id: string; pgCode: string; status: string }>;
  redCards: Array<{ id: string; title: string; blockedReason: string | null }>;
  recentLogs: Array<{ id: string; content: string; logDate: string }>;
  issues: Array<{ id: string; description: string; status: string }>;
  finance: { invoices: Array<{ amountCents: string }>; receipts: Array<{ amountCents: string }> };
  team: Array<{ id: string; displayName: string; position: string | null }>;
}

const STATE_COLOR: Record<string, string> = { done: 'green', current: 'blue', todo: 'gray' };

/** 08 册 §五 BV-03 项目监管看板（FR-M10-027 五区：R-PM 临时授权视图） */
export default function ProjectMonitor() {
  const { projectId } = useParams();
  const [d, setD] = useState<ProjectMonitorData | null>(null);

  useEffect(() => {
    if (!projectId) return;
    api<ProjectMonitorData>('get', `/api/v1/kb/project/${projectId}`)
      .then(setD)
      .catch(toastError);
  }, [projectId]);

  if (!d) return null;
  const wan = (cents: string | null) => (cents ? (Number(cents) / 1_000_000).toFixed(2) : '—');

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Card size="small" title={`BV-03 项目监管（FR-M10-027）｜${d.project.projectCode} ${d.project.standardName}`}>
        <Timeline
          mode="left"
          items={d.timeline.map((t) => ({
            color: STATE_COLOR[t.state] ?? 'gray',
            children: `${t.stage}${t.state === 'current' ? ' ●' : t.state === 'done' ? ' ✓' : ''}`,
          }))}
        />
      </Card>

      <Card size="small" title="② 关键节点卡">
        <Row gutter={16}>
          <Col span={6}>
            <Statistic title="合同金额（万元）" value={wan(d.keyNodes.contractAmountCents)} />
          </Col>
          <Col span={6}>
            <Statistic title="累计回款（万元）" value={wan(d.keyNodes.receiptCents)} />
          </Col>
          <Col span={6}>
            <Statistic
              title="齐套缺料工单"
              value={d.keyNodes.kitShort}
              valueStyle={{ color: d.keyNodes.kitShort > 0 ? '#ff4d4f' : undefined }}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title="里程碑逾期"
              value={d.keyNodes.milestoneOverdue}
              valueStyle={{ color: d.keyNodes.milestoneOverdue > 0 ? '#fa8c16' : undefined }}
            />
          </Col>
        </Row>
        {d.project.pendingFlags.length > 0 && (
          <div style={{ marginTop: 8 }}>
            {d.project.pendingFlags.map((f) => (
              <Tag key={f} color="orange">
                {f === 'pending_farm' ? '待关联风场' : f === 'pending_owner' ? '待补业主' : f}
              </Tag>
            ))}
          </div>
        )}
      </Card>

      <Row gutter={12}>
        <Col xs={24} md={12}>
          <Card size="small" title={`③ 风险与红灯（${d.redCards.length}）`}>
            {d.redCards.length === 0 && <span style={{ color: '#999' }}>本项目无红灯</span>}
            {d.redCards.map((c) => (
              <div key={c.id} style={{ background: '#fff1f0', border: '1px solid #ffccc7', borderRadius: 8, padding: 8, marginBottom: 6, fontSize: 13 }}>
                🔴 {c.title}
              </div>
            ))}
          </Card>
          <Card size="small" title="里程碑" style={{ marginTop: 12 }}>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={d.milestones}
              columns={[
                { title: '节点', dataIndex: 'name', width: 100 },
                { title: '计划', dataIndex: 'planDate', width: 110, render: (v: string | null) => (v ? new Date(v).toLocaleDateString('zh-CN') : '—') },
                { title: '实际', dataIndex: 'actualDate', width: 110, render: (v: string | null) => (v ? new Date(v).toLocaleDateString('zh-CN') : '—') },
                { title: '状态', dataIndex: 'status', width: 80, render: (s: string) => <Tag color={s === 'done' ? 'green' : 'default'}>{s === 'done' ? '完成' : '进行'}</Tag> },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card size="small" title="④ 团队与动态">
            <Descriptions size="small" column={1}>
              {d.team.map((t) => (
                <Descriptions.Item key={t.id} label={t.position ?? '成员'}>
                  {t.displayName}
                </Descriptions.Item>
              ))}
            </Descriptions>
            <div style={{ marginTop: 8 }}>
              {d.recentLogs.length === 0 && <span style={{ color: '#999' }}>暂无现场日志</span>}
              {d.recentLogs.map((l) => (
                <div key={l.id} style={{ fontSize: 12, color: '#666', marginBottom: 4 }}>
                  · [{new Date(l.logDate).toLocaleDateString('zh-CN')}] {l.content.slice(0, 50)}
                </div>
              ))}
            </div>
          </Card>
          <Card size="small" title="回款/开票（⑤ 操作区数据源）" style={{ marginTop: 12 }}>
            <Statistic title="累计开票（万元）" value={wan(String(d.finance.invoices.reduce((s, i) => s + Number(i.amountCents), 0)))} />
            <Statistic title="累计回款（万元）" value={wan(d.finance.receipts.reduce((s, r) => String(Number(r.amountCents) + s), '0'))} />
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
