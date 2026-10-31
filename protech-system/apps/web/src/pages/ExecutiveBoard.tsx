import { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { api, toastError } from '../api/client';

interface ExecData {
  metrics: Array<{ metricCode: string; name: string; unit: string; target: string | null; direction: string; value: string | null; status: string }>;
  redTotal: number;
  escalated: number;
  noResponse: number;
  redByBoard: Array<{ boardCode: string; name: string; red: number }>;
  health: Array<{ boardCode: string; name: string; type: string; red: number; lastMeetingAt: string | null }>;
  risks: Array<{ type: string; text: string }>;
}

function lightColor(m: { value: string | null; target: string | null; direction: string }): string {
  if (m.value === null) return '#d9d9d9';
  const t = Number(m.target);
  if (!Number.isFinite(t) || t === 0) return '#d9d9d9';
  const v = Number(m.value);
  const ratio = m.direction === 'reverse' ? (v <= 0 ? 1 : t / v) : v / t;
  return ratio >= 1 ? '#52c41a' : ratio >= 0.8 ? '#faad14' : '#ff4d4f';
}

/** 08 册 §四 BV-02 全局看板（总经理视图 FR-M10-026）：五区 */
export default function ExecutiveBoard() {
  const nav = useNavigate();
  const [d, setD] = useState<ExecData | null>(null);

  useEffect(() => {
    api<ExecData>('get', '/api/v1/kb/executive')
      .then(setD)
      .catch(toastError);
  }, []);

  if (!d) return null;

  return (
    <Row gutter={[12, 12]}>
      <Col span={24}>
        <Card size="small" title="经营指标区（自动取数 · 金额不脱敏）" extra={<span style={{ color: '#999', fontSize: 12 }}>最近快照时点见指标卡</span>}>
          <Row gutter={12}>
            {d.metrics.map((m) => (
              <Col key={m.metricCode} xs={12} md={6} lg={4}>
                <Statistic
                  title={<span style={{ fontSize: 12 }}>{m.name}</span>}
                  value={m.value === null ? '—' : m.unit === 'pct' ? `${(Number(m.value) * 100).toFixed(1)}%` : m.value}
                  valueStyle={{ color: lightColor(m), fontSize: 24 }}
                  suffix={m.value === null ? undefined : <span style={{ fontSize: 11, color: '#999' }}>目标 {m.target ?? '—'}</span>}
                />
              </Col>
            ))}
          </Row>
        </Card>
      </Col>

      <Col xs={24} md={8}>
        <Card size="small" title="红灯总览区">
          <Row gutter={12}>
            <Col span={8}>
              <Statistic title="红灯总数" value={d.redTotal} valueStyle={{ color: '#ff4d4f' }} />
            </Col>
            <Col span={8}>
              <Statistic title="已升级总板" value={d.escalated} valueStyle={{ color: '#fa541c' }} />
            </Col>
            <Col span={8}>
              <Statistic title="48h 未响应" value={d.noResponse} valueStyle={{ color: '#fa8c16' }} />
            </Col>
          </Row>
          <div style={{ marginTop: 12 }}>
            {d.redByBoard
              .filter((b) => b.red > 0)
              .map((b) => (
                <Tag key={b.boardCode} color="red" style={{ marginBottom: 4 }} onClick={() => nav(`/kb/${b.boardCode}`)}>
                  {b.name}: {b.red}
                </Tag>
              ))}
          </div>
        </Card>
      </Col>

      <Col xs={24} md={8}>
        <Card size="small" title="风险预警区">
          {d.risks.length === 0 ? (
            <span style={{ color: '#999', fontSize: 12 }}>齐套缺口 / 呆滞新增 / 账龄恶化 / 里程碑逾期预警随 S3/S4 模块上线接入（FR-M10-006 可下钻）</span>
          ) : (
            d.risks.map((r, i) => (
              <div key={i}>
                <Tag color="orange">{r.type}</Tag>
                {r.text}
              </div>
            ))
          )}
        </Card>
      </Col>

      <Col xs={24} md={8}>
        <Card size="small" title="九板健康度（最近开会时间联动未开会提醒 FR-M10-022）">
          <Table
            rowKey="boardCode"
            size="small"
            pagination={false}
            dataSource={d.health}
            onRow={(r) => ({ onClick: () => nav(`/kb/${r.boardCode}`), style: { cursor: 'pointer' } })}
            columns={[
              { title: '板', dataIndex: 'name', ellipsis: true },
              { title: '红灯', dataIndex: 'red', width: 60, render: (v: number) => (v > 0 ? <Tag color="red">{v}</Tag> : 0) },
              {
                title: '最近开会',
                dataIndex: 'lastMeetingAt',
                width: 100,
                render: (v: string | null) =>
                  v ? (
                    new Date(v).toLocaleDateString('zh-CN')
                  ) : (
                    <Tag color="orange">未开</Tag>
                  ),
              },
            ]}
          />
        </Card>
      </Col>
    </Row>
  );
}
