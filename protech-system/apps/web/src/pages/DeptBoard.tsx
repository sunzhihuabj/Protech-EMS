import { useEffect, useState } from 'react';
import { Card, Col, Row, Select, Space, Statistic, Table, Tag } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { api, toastError } from '../api/client';

interface DeptView {
  dept: { id: string; name: string; chainCode: string | null; boardCode: string | null; headcount: number | null };
  memberCount: number;
  kpis: Array<{ metricCode: string; name: string; value: string | null; unit: string; target: string | null; direction: string }>;
  projects: Array<{ id: string; projectCode: string; standardName: string; status: string }>;
  redCards: Array<{ id: string; title: string; blockedReason: string | null }>;
  recentMoves: Array<{ id: string; moveType: string; sourceId: string; qty: number }>;
  load: Array<{ userId: string; displayName: string; position: string | null; projectCount: number; overdueCards: number; redCards: number }>;
}

/** 08 册 §三 BV-01 部门看板（FR-M10-025 六区） */
export default function DeptBoard() {
  const { deptId } = useParams();
  const nav = useNavigate();
  const [depts, setDepts] = useState<Array<{ id: string; name: string }>>([]);
  const [d, setD] = useState<DeptView | null>(null);

  useEffect(() => {
    api<Array<{ id: string; name: string }>>('get', '/api/v1/sys/depts')
      .then(setDepts)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!deptId) return;
    api<DeptView>('get', `/api/v1/kb/dept/${deptId}`)
      .then(setD)
      .catch(toastError);
  }, [deptId]);

  if (!d) return null;
  const color = (m: { value: string | null; target: string | null; direction: string }) => {
    if (m.value === null) return '#d9d9d9';
    const t = Number(m.target);
    if (!Number.isFinite(t) || t === 0) return '#d9d9d9';
    const ratio = m.direction === 'reverse' ? (Number(m.value) <= 0 ? 1 : t / Number(m.value)) : Number(m.value) / t;
    return ratio >= 1 ? '#52c41a' : ratio >= 0.8 ? '#faad14' : '#ff4d4f';
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Card size="small" title="BV-01 部门看板（FR-M10-025）">
        <Select
          style={{ width: 240 }}
          value={deptId}
          onChange={(v) => nav(`/kb/dept/${v}`)}
          options={depts.map((x) => ({ value: x.id, label: x.name }))}
        />
        <Row gutter={16} style={{ marginTop: 12 }}>
          <Col span={6}>
            <Statistic title="部门人数" value={d.memberCount} suffix={d.dept.boardCode ? `｜${d.dept.boardCode} 板` : ''} />
          </Col>
          <Col span={18}>
            <Row gutter={12}>
              {d.kpis.map((m) => (
                <Col key={m.metricCode} span={6}>
                  <Statistic
                    title={<span style={{ fontSize: 12 }}>{m.name}</span>}
                    value={m.value === null ? '—' : m.unit === 'pct' ? `${(Number(m.value) * 100).toFixed(1)}%` : m.value}
                    valueStyle={{ color: color(m), fontSize: 20 }}
                  />
                </Col>
              ))}
            </Row>
          </Col>
        </Row>
      </Card>

      <Row gutter={12}>
        <Col xs={24} md={12}>
          <Card size="small" title="项目卡片区（成员担任责任人）">
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={d.projects}
              onRow={(r) => ({ onClick: () => nav(`/kb/project/${r.id}`), style: { cursor: 'pointer' } })}
              columns={[
                { title: '编码', dataIndex: 'projectCode', width: 120 },
                { title: '标准名', dataIndex: 'standardName', ellipsis: true },
                { title: '状态', dataIndex: 'status', width: 110 },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card size="small" title={`红灯区（${d.redCards.length}）`}>
            {d.redCards.length === 0 && <span style={{ color: '#999' }}>无红灯</span>}
            {d.redCards.map((c) => (
              <div key={c.id} style={{ background: '#fff1f0', border: '1px solid #ffccc7', borderRadius: 8, padding: '6px 10px', marginBottom: 6, fontSize: 13 }}>
                🔴 {c.title}
                {c.blockedReason && <div style={{ color: '#cf1322', fontSize: 12 }}>{c.blockedReason}</div>}
              </div>
            ))}
          </Card>
        </Col>
      </Row>

      <Row gutter={12}>
        <Col xs={24} md={12}>
          <Card size="small" title="人员负荷区（督办依据）">
            <Table
              rowKey="userId"
              size="small"
              pagination={false}
              dataSource={d.load}
              columns={[
                { title: '成员', dataIndex: 'displayName', width: 100 },
                { title: '岗位', dataIndex: 'position', ellipsis: true },
                { title: '在办项目', dataIndex: 'projectCount', width: 90, align: 'right' },
                { title: '逾期卡', dataIndex: 'overdueCards', width: 80, render: (v: number) => (v > 0 ? <Tag color="red">{v}</Tag> : 0) },
                { title: '红灯', dataIndex: 'redCards', width: 70, render: (v: number) => (v > 0 ? <Tag color="volcano">{v}</Tag> : 0) },
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card size="small" title="单据动态区（成员经手）">
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={d.recentMoves}
              columns={[
                { title: '方向', dataIndex: 'moveType', width: 70, render: (v: string) => <Tag color={v === 'in' ? 'green' : 'red'}>{v === 'in' ? '入库' : '出库'}</Tag> },
                { title: '单据', dataIndex: 'sourceId' },
                { title: '数量', dataIndex: 'qty', width: 80, align: 'right' },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </Space>
  );
}

