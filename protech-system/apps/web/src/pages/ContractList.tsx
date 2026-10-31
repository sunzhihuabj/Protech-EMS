import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Drawer, Form, Input, InputNumber, Select, Space, Table, Tag, App, Steps } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { api, toastError } from '../api/client';
import { useAuth } from '../stores/auth';
import { BlockCodeAlert } from '../components/BlockCodeAlert';

interface ContractRow {
  id: string;
  contractCode: string;
  contractName: string;
  nature: string;
  amountCents: string;
  status: string;
  reviewStatus: string;
  invoicingLocked: boolean;
}

const STATUS: Record<string, string> = {
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

/** 07 册 P1：合同台账（BR-01 建档入口 + 评审/用印/备案操作按状态出现） */
export default function ContractList() {
  const nav = useNavigate();
  const { message } = App.useApp();
  const has = useAuth((s) => s.has);
  const [rows, setRows] = useState<ContractRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const [err, setErr] = useState<unknown>(null);
  const [options, setOptions] = useState<{ parties: Array<{ id: string; name: string }>; entities: Array<{ id: string; entityCode: string; name: string }> }>({
    parties: [],
    entities: [],
  });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const d = await api<{ list: ContractRow[]; total: number }>('get', '/api/v1/contracts', undefined, {
        page,
        pageSize: 20,
        keyword: keyword || undefined,
      });
      setRows(d.list);
      setTotal(d.total);
    } catch (e) {
      toastError(e);
    } finally {
      setLoading(false);
    }
  }, [page, keyword]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDrawer = async () => {
    setOpen(true);
    setErr(null);
    form.resetFields();
    try {
      const [parties, entities] = await Promise.all([
        api<{ list: Array<{ id: string; name: string }> }>('get', '/api/v1/mdm/parties?pageSize=200'),
        api<Array<{ id: string; entityCode: string; name: string }>>('get', '/api/v1/mdm/entities'),
      ]);
      setOptions({ parties: parties.list, entities });
    } catch (e) {
      toastError(e);
    }
  };

  const submit = async () => {
    const v = await form.validateFields();
    try {
      const created = await api<{ contractCode: string }>('post', '/api/v1/contracts', v);
      message.success(`合同建档成功：${created.contractCode}（BR-09 编码不可改）`);
      setOpen(false);
      void load();
    } catch (e) {
      setErr(e);
      toastError(e);
    }
  };

  return (
    <Card
      title="合同台账（25 要素 · 两个建档卡点）"
      extra={
        <Space>
          <Input.Search
            placeholder="编号/名称"
            allowClear
            onSearch={(v) => {
              setPage(1);
              setKeyword(v);
            }}
            style={{ width: 200 }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load()} />
          {has('contract:create') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => void openDrawer()}>
              合同建档
            </Button>
          )}
        </Space>
      }
    >
      <Table<ContractRow>
        rowKey="id"
        size="middle"
        loading={loading}
        dataSource={rows}
        pagination={{ current: page, total, pageSize: 20, onChange: setPage, showTotal: (t) => `共 ${t} 条` }}
        onRow={(r) => ({ onClick: () => nav(`/biz/contract/${r.id}`), style: { cursor: 'pointer' } })}
        columns={[
          { title: '合同编号', dataIndex: 'contractCode', width: 190 },
          { title: '合同名称', dataIndex: 'contractName', ellipsis: true },
          { title: '性质', dataIndex: 'nature', width: 70, render: (n: string) => <Tag>{n}</Tag> },
          {
            title: '金额（万元）',
            dataIndex: 'amountCents',
            width: 110,
            align: 'right',
            render: (v: string) => (Number(v) / 1_000_000).toFixed(2),
          },
          {
            title: '评审',
            dataIndex: 'reviewStatus',
            width: 90,
            render: (s: string) =>
              s === 'approved' ? (
                <Tag color="green">已通过</Tag>
              ) : s === 'reviewing' ? (
                <Tag color="blue">评审中</Tag>
              ) : s === 'rejected' ? (
                <Tag color="red">已驳回</Tag>
              ) : (
                '—'
              ),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (s: string) => <Tag color={s === 'performing' ? 'blue' : s === 'signed' ? 'cyan' : 'default'}>{STATUS[s] ?? s}</Tag>,
          },
          {
            title: '开票',
            dataIndex: 'invoicingLocked',
            width: 80,
            render: (locked: boolean) => (locked ? <Tag color="red">BR-02 冻结</Tag> : '—'),
          },
        ]}
      />

      <Drawer
        title="合同建档（评审前先挂项目：BR-01）"
        width={500}
        open={open}
        onClose={() => setOpen(false)}
        extra={
          <Button type="primary" onClick={() => void submit()}>
            提交
          </Button>
        }
      >
        <Form form={form} layout="vertical" initialValues={{ nature: 'XS', bizType: 'SB' }}>
          <Form.Item name="contractName" label="合同名称" rules={[{ required: true }]}>
            <Input placeholder="按'对方主体-项目/事项'" />
          </Form.Item>
          <Space.Compact block>
            <Form.Item name="nature" label="性质（E-06）" rules={[{ required: true }]} style={{ width: 120 }}>
              <Select
                options={[
                  { value: 'XS', label: 'XS 销售' },
                  { value: 'CG', label: 'CG 采购外包' },
                  { value: 'KJ', label: 'KJ 框架' },
                ]}
              />
            </Form.Item>
            <Form.Item name="bizType" label="业务码" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Select
                options={[
                  { value: 'SB', label: 'SB 设备' },
                  { value: 'GC', label: 'GC 工程' },
                  { value: 'WX', label: 'WX 维保' },
                ]}
              />
            </Form.Item>
          </Space.Compact>
          <Form.Item name="ourEntityId" label="我方签约主体" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={options.entities.map((e) => ({ value: e.id, label: `${e.entityCode} ${e.name}` }))} />
          </Form.Item>
          <Form.Item name="counterpartyId" label="对方主体（BR-08 下拉）" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={options.parties.map((p) => ({ value: p.id, label: p.name }))} />
          </Form.Item>
          <Form.Item name="amountCents" label="合同金额（分，BR-10）" rules={[{ required: true }]}>
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="taxRate" label="税率（如 0.09）">
            <InputNumber min={0} max={1} step={0.01} style={{ width: '100%' }} />
          </Form.Item>
          {err !== null && <BlockCodeAlert error={err} />}
          <Steps
            size="small"
            current={0}
            items={[
              { title: '建档' },
              { title: '挂项目' },
              { title: '提交评审（BR-01）' },
              { title: '用印（BR-02 起算）' },
              { title: '25 要素备案' },
            ]}
          />
        </Form>
      </Drawer>
    </Card>
  );
}
