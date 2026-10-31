import { useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Input, Modal, Space, Steps, Table, Tag, Timeline, App } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { api, toastError } from '../api/client';
import { useAuth } from '../stores/auth';
import { BlockCodeAlert } from '../components/BlockCodeAlert';

interface ContractDetailData {
  id: string;
  contractCode: string;
  contractName: string;
  nature: string;
  bizType: string;
  amountCents: string;
  taxRate: string | null;
  status: string;
  reviewStatus: string;
  sealAt: string | null;
  filedAt: string | null;
  archive: {
    sealedAt: string | null;
    archiveDeadlineAt: string | null;
    filedAt: string | null;
    overdue: boolean;
    invoicingLocked: boolean;
    elementsFilled: number;
    elementsTotal: number;
  };
  projects: Array<{ id: string; projectCode: string; standardName: string; status: string }>;
  reviews: Array<{ id: string; round: number; role: string; result: string | null; opinion: string | null }>;
  changes: Array<{ id: string; changeCode: string; reason: string; status: string }>;
}

const ROLE_LABEL: Record<string, string> = { biz: '商务', fin: '财务', eng: '工程', mfg: '生产' };
const STATUS_LABEL: Record<string, string> = {
  drafting: '起草中',
  reviewed: '已评审',
  signed: '已签署',
  performing: '履行中',
  changed: '已变更',
  partial_invoiced: '部分开票回款',
  accepted: '已验收',
  warranty: '质保期中',
  closed: '已关闭',
};

/** 07 册 3.2 合同详情（P2+P6）：四组要素 + 关联 Tab + 评审时间线 + BR-01/02 卡点呈现 */
export default function ContractDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const { message } = App.useApp();
  const has = useAuth((s) => s.has);
  const [d, setD] = useState<ContractDetailData | null>(null);
  const [err, setErr] = useState<unknown>(null);
  const [attachOpen, setAttachOpen] = useState(false);
  const [projectId, setProjectId] = useState('');

  const load = () => {
    api<ContractDetailData>('get', `/api/v1/contracts/${id}`)
      .then(setD)
      .catch(toastError);
  };
  useEffect(load, [id]);

  if (!d) return null;

  const stepIdx = ['drafting', 'reviewed', 'signed', 'performing'].indexOf(d.status);

  const submitReview = async () => {
    setErr(null);
    try {
      await api('post', `/api/v1/contracts/${d.id}/review/submit`, {});
      message.success('已提交评审（四角色会签）');
      load();
    } catch (e) {
      setErr(e);
      toastError(e);
    }
  };

  const seal = async () => {
    try {
      await api('post', `/api/v1/contracts/${d.id}/seal`, { sealCopies: 1 });
      message.success('用印登记完成；建档时限已起算（BR-02：3 个工作日）');
      load();
    } catch (e) {
      toastError(e);
    }
  };

  const reviewAct = async (role: string, approved: boolean) => {
    let opinion = '';
    Modal.confirm({
      title: `${ROLE_LABEL[role]}会签（${approved ? '通过' : '驳回'}）`,
      content: <Input.TextArea placeholder="意见（驳回建议说明原因）" onChange={(e) => (opinion = e.target.value)} />,
      onOk: async () => {
        try {
          await api('post', `/api/v1/contracts/${d!.id}/review/act`, { role, approved, opinion: opinion || undefined });
          message.success('会签完成');
          load();
        } catch (e) {
          toastError(e);
          return Promise.reject();
        }
      },
    });
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card
        size="small"
        title={
          <Space>
            <span>{d.contractCode}</span>
            <span>{d.contractName}</span>
            <Tag color="blue">{STATUS_LABEL[d.status] ?? d.status}</Tag>
            {d.archive.invoicingLocked && <Tag color="red">BR-02 开票冻结</Tag>}
          </Space>
        }
        extra={
          <Space>
            {d.status === 'drafting' && has('contract:review:submit') && (
              <Button type="primary" onClick={() => void submitReview()}>
                提交评审（BR-01）
              </Button>
            )}
            {d.status === 'reviewed' && has('contract:seal') && (
              <Button type="primary" onClick={() => void seal()}>
                用印登记
              </Button>
            )}
            {d.status === 'signed' && !d.filedAt && has('contract:filing') && (
              <Button type="primary" onClick={() => nav('/biz/contract')}>
                25 要素备案
              </Button>
            )}
          </Space>
        }
      >
        <Steps
          size="small"
          current={stepIdx < 0 ? 4 : stepIdx}
          items={[
            { title: '起草' },
            { title: '评审（BR-01）' },
            { title: '用印' },
            { title: '履行/备案（BR-02）' },
          ]}
        />
        {err !== null && <BlockCodeAlert error={err} />}
        {d.archive.invoicingLocked && (
          <Alert
            type="error"
            showIcon
            style={{ marginTop: 12 }}
            message="BR-02 该合同已用印超过 3 个工作日未建档，开票申请已被冻结"
            description="请先完成 25 要素建档备案（履行中 → 备案后自动解锁）"
          />
        )}
      </Card>

      <Card size="small" title="A/B 商务要素">
        <Descriptions size="small" column={3}>
          <Descriptions.Item label="性质/业务码">{d.nature} / {d.bizType}</Descriptions.Item>
          <Descriptions.Item label="金额（万元）">{(Number(d.amountCents) / 1_000_000).toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="税率">{d.taxRate ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="建档时限">{d.archive.archiveDeadlineAt ? new Date(d.archive.archiveDeadlineAt).toLocaleDateString('zh-CN') : '未用印'}</Descriptions.Item>
          <Descriptions.Item label="25 要素进度">
            {d.archive.elementsFilled}/{d.archive.elementsTotal}
          </Descriptions.Item>
          <Descriptions.Item label="备案时间">{d.filedAt ? new Date(d.filedAt).toLocaleDateString('zh-CN') : '未备案'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card
        size="small"
        title="关联项目（一合同多项目，BR-01 前提）"
        extra={
          has('contract:create') && (
            <Button size="small" onClick={() => setAttachOpen(true)}>
              挂项目
            </Button>
          )
        }
      >
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={d.projects}
          onRow={(r) => ({ onClick: () => nav(`/project/${r.id}`), style: { cursor: 'pointer' } })}
          columns={[
            { title: '项目编码', dataIndex: 'projectCode', width: 130 },
            { title: '标准名', dataIndex: 'standardName' },
            { title: '状态', dataIndex: 'status', width: 120 },
          ]}
        />
      </Card>

      <Card size="small" title="评审时间线（会签：商务/财务/工程/生产）">
        {d.reviews.length === 0 ? (
          <span style={{ color: '#999' }}>未提交评审</span>
        ) : (
          <Timeline
            items={d.reviews.map((r) => ({
              color: r.result === 'approved' ? 'green' : r.result === 'rejected' ? 'red' : 'gray',
              children: `第 ${r.round} 轮 · ${ROLE_LABEL[r.role] ?? r.role}：${r.result === 'approved' ? '通过' : r.result === 'rejected' ? '驳回' : '待会签'}${r.opinion ? `（${r.opinion}）` : ''}`,
            }))}
          />
        )}
        {d.reviewStatus === 'reviewing' &&
          has('contract:review:act') &&
          d.reviews
            .filter((r) => r.result === null)
            .map((r) => (
              <Space key={r.id} style={{ marginRight: 12 }}>
                <Button size="small" type="primary" ghost onClick={() => void reviewAct(r.role, true)}>
                  {ROLE_LABEL[r.role]}通过
                </Button>
                <Button size="small" danger onClick={() => void reviewAct(r.role, false)}>
                  {ROLE_LABEL[r.role]}驳回
                </Button>
              </Space>
            ))}
      </Card>

      {d.changes.length > 0 && (
        <Card size="small" title="补充协议（-BCn）">
          {d.changes.map((c) => (
            <div key={c.id}>
              <Tag color={c.status === 'approved' ? 'green' : c.status === 'rejected' ? 'red' : 'blue'}>{c.changeCode}</Tag>
              {c.reason}
            </div>
          ))}
        </Card>
      )}

      <Modal
        title="挂项目（选择已有项目档案）"
        open={attachOpen}
        onOk={async () => {
          if (!projectId) return;
          try {
            await api('post', `/api/v1/contracts/${d.id}/projects`, { projectId });
            message.success('已关联');
            setAttachOpen(false);
            load();
          } catch (e) {
            toastError(e);
          }
        }}
        onCancel={() => setAttachOpen(false)}
      >
        <Input placeholder="输入项目 ID（从项目列表复制）" value={projectId} onChange={(e) => setProjectId(e.target.value)} />
      </Modal>
    </Space>
  );
}
