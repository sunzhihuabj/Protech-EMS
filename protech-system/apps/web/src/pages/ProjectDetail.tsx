import { useEffect, useState } from 'react';
import { Card, Descriptions, Tag, Progress, Timeline, Button, Space, App } from 'antd';
import { useParams, useNavigate } from 'react-router-dom';
import { api, toastError } from '../api/client';
import { useAuth } from '../stores/auth';

interface ProjectDetailData {
  id: string;
  projectCode: string;
  standardName: string;
  shortName: string;
  phase: string | null;
  status: string;
  orderType: string;
  productLines: string[];
  unitCount: number;
  contractAmountCents: string | null;
  pendingFlags: string[];
  farm: { farmCode: string; fullName: string } | null;
  owner: { partyCode: string; name: string } | null;
  completeness: { filled: number; total: number; percent: number; missing: string[] };
  productItems: Array<{ id: string; productLine: string; unitCount: number; contractCode: string | null }>;
  aliases: Array<{ alias: string }>;
  createdAt: string;
}

const FLOW = ['pending_farm', 'pending_exec', 'executing', 'shipped', 'accepted', 'warranty', 'closed'];
const FLOW_LABEL: Record<string, string> = {
  pending_farm: '待关联风场',
  pending_exec: '签约待执行',
  executing: '执行中',
  shipped: '已发货',
  accepted: '已验收',
  warranty: '质保期中',
  closed: '已关闭',
};

/** 07 册 3.3 项目详情：头部 + 27 字段分组卡 + 关联 + 状态时间线（BV-03 数据源页，S2 深化） */
export default function ProjectDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const { message } = App.useApp();
  const has = useAuth((s) => s.has);
  const [d, setD] = useState<ProjectDetailData | null>(null);

  const load = () => {
    api<ProjectDetailData>('get', `/api/v1/mdm/projects/${id}`)
      .then(setD)
      .catch(toastError);
  };
  useEffect(load, [id]);

  if (!d) return null;

  const idx = FLOW.indexOf(d.status);

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card
        size="small"
        title={
          <Space>
            <span>{d.projectCode}</span>
            <span>{d.standardName}</span>
            <Tag color="blue">{FLOW_LABEL[d.status] ?? d.status}</Tag>
          </Space>
        }
        extra={
          has('mdm:project:update') && (
            <Button
              size="small"
              onClick={async () => {
                const next = FLOW[idx + 1];
                if (!next) return;
                try {
                  await api('post', `/api/v1/mdm/projects/${d.id}/transition`, { to: next });
                  message.success(`状态迁移至：${FLOW_LABEL[next]}`);
                  load();
                } catch (e) {
                  toastError(e);
                }
              }}
            >
              推进到下一状态
            </Button>
          )
        }
      >
        <Timeline
          mode="left"
          items={FLOW.map((s, i) => ({
            color: i < idx ? 'green' : i === idx ? 'blue' : 'gray',
            children: FLOW_LABEL[s],
          }))}
        />
      </Card>

      <Card size="small" title={`档案完整度（关键 12 项，FR-M01-008）`}>
        <Progress percent={d.completeness.percent} status={d.completeness.percent >= 95 ? 'success' : 'active'} />
        {d.completeness.missing.length > 0 && (
          <div style={{ color: '#999', fontSize: 12 }}>缺失：{d.completeness.missing.join('、')}</div>
        )}
      </Card>

      <Card size="small" title="基本信息（27 字段分组卡）">
        <Descriptions size="small" column={3}>
          <Descriptions.Item label="常用简称">{d.shortName}</Descriptions.Item>
          <Descriptions.Item label="期次">{d.phase ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="订单类型">{d.orderType}</Descriptions.Item>
          <Descriptions.Item label="风场">
            {d.farm ? `${d.farm.farmCode} ${d.farm.fullName}` : '⚠️ 待关联风场'}
          </Descriptions.Item>
          <Descriptions.Item label="业主">{d.owner ? `${d.owner.partyCode} ${d.owner.name}` : '⚠️ 待补业主'}</Descriptions.Item>
          <Descriptions.Item label="产品线">{d.productLines.join('、')}</Descriptions.Item>
          <Descriptions.Item label="台数/套数">{d.unitCount}</Descriptions.Item>
          <Descriptions.Item label="合同金额（分）">{d.contractAmountCents ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="合同金额（万元）">
            {d.contractAmountCents ? (Number(d.contractAmountCents) / 1_000_000).toFixed(2) : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="别名">{d.aliases.map((a) => a.alias).join('、') || '—'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card size="small" title="产品子项（FR-M04-009 一风场多产品）">
        {d.productItems.length === 0 ? (
          <span style={{ color: '#999' }}>暂无子项——一风场多产品时在此拆行（不拆项目档案）</span>
        ) : (
          <Descriptions size="small" column={1}>
            {d.productItems.map((i) => (
              <Descriptions.Item key={i.id} label={i.productLine}>
                {i.unitCount} 台/套 {i.contractCode ? `· ${i.contractCode}` : ''}
              </Descriptions.Item>
            ))}
          </Descriptions>
        )}
      </Card>
    </Space>
  );
}
