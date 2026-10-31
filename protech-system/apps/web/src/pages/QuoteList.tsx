import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Drawer, Form, Input, InputNumber, Select, Space, Table, Tag, App, Alert } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { api, toastError } from '../api/client';
import { useAuth } from '../stores/auth';

interface QuoteRow {
  id: string;
  quotationCode: string;
  partyId: string;
  productLines: string[];
  qty: number;
  unitPriceCents: string;
  totalCents: string;
  status: string;
  expectedSignAt: string | null;
  convertedContractId: string | null;
}

const STATUS: Record<string, { label: string; color: string }> = {
  draft: { label: '草稿', color: 'default' },
  sent: { label: '已发出', color: 'blue' },
  won: { label: '中标', color: 'green' },
  lost: { label: '未中标', color: 'red' },
  expired: { label: '失效', color: 'default' },
};

/** 07 册 P1：报价列表 + 登记抽屉（多头提示 FR-M02-002 / 中标签约时间 FR-M02-004） */
export default function QuoteList() {
  const nav = useNavigate();
  const { message, modal } = App.useApp();
  const has = useAuth((s) => s.has);
  const [rows, setRows] = useState<QuoteRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const [hints, setHints] = useState<Array<{ quotationCode: string; totalCents: string; status: string }>>([]);
  const [options, setOptions] = useState<{ parties: Array<{ id: string; name: string }> }>({ parties: [] });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const d = await api<{ list: QuoteRow[]; total: number }>('get', '/api/v1/quotations', undefined, { page, pageSize: 20 });
      setRows(d.list);
      setTotal(d.total);
    } catch (e) {
      toastError(e);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDrawer = async () => {
    setOpen(true);
    setHints([]);
    form.resetFields();
    try {
      const parties = await api<{ list: Array<{ id: string; name: string }> }>('get', '/api/v1/mdm/parties?pageSize=200');
      setOptions({ parties: parties.list });
    } catch (e) {
      toastError(e);
    }
  };

  const onValuesChange = async () => {
    const v = form.getFieldsValue();
    if (!v.partyId || !v.productLines?.length) return setHints([]);
    try {
      const d = await api<{ hints: typeof hints }>('post', '/api/v1/quotations/check-multi', {
        partyId: v.partyId,
        productLines: v.productLines,
      });
      setHints(d.hints);
    } catch {
      setHints([]);
    }
  };

  const submit = async () => {
    const v = await form.validateFields();
    try {
      const created = await api<{ quotationCode: string }>('post', '/api/v1/quotations', v);
      message.success(`报价登记成功：${created.quotationCode}`);
      setOpen(false);
      void load();
    } catch (e) {
      toastError(e);
    }
  };

  const act = async (id: string, to: string) => {
    let expectedSignAt: string | undefined;
    if (to === 'won') {
      expectedSignAt = modal
        ? undefined
        : undefined;
      void expectedSignAt;
    }
    if (to === 'won') {
      // 中标必填预计签约时间（FR-M02-004）
      let input = '';
      modal.confirm({
        title: '登记中标',
        content: (
          <Input
            placeholder="预计签约时间（如 2026-12-01）"
            onChange={(e) => (input = e.target.value)}
          />
        ),
        onOk: async () => {
          if (!input) {
            message.error('中标必须填写预计签约时间');
            return Promise.reject();
          }
          try {
            await api('post', `/api/v1/quotations/${id}/transition`, { to, expectedSignAt: input });
            message.success('已登记中标');
            void load();
          } catch (e) {
            toastError(e);
            return Promise.reject();
          }
        },
      });
      return;
    }
    try {
      await api('post', `/api/v1/quotations/${id}/transition`, { to });
      message.success(`状态已更新：${STATUS[to]?.label ?? to}`);
      void load();
    } catch (e) {
      toastError(e);
    }
  };

  return (
    <Card
      title="报价管理（有依据、可沉淀、中标回流）"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void load()} />
          {has('quote:create') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => void openDrawer()}>
              报价登记
            </Button>
          )}
        </Space>
      }
    >
      <Table<QuoteRow>
        rowKey="id"
        size="middle"
        loading={loading}
        dataSource={rows}
        pagination={{ current: page, total, pageSize: 20, onChange: setPage, showTotal: (t) => `共 ${t} 条` }}
        columns={[
          { title: '报价编号', dataIndex: 'quotationCode', width: 130 },
          {
            title: '产品线',
            dataIndex: 'productLines',
            render: (ps: string[]) => ps.map((p) => <Tag key={p}>{p}</Tag>),
          },
          { title: '数量', dataIndex: 'qty', width: 70, align: 'right' },
          {
            title: '单价（元）',
            dataIndex: 'unitPriceCents',
            width: 110,
            align: 'right',
            render: (v: string) => (Number(v) / 100).toFixed(2),
          },
          {
            title: '总价（万元）',
            dataIndex: 'totalCents',
            width: 110,
            align: 'right',
            render: (v: string) => (Number(v) / 1_000_000).toFixed(2),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (s: string) => <Tag color={STATUS[s]?.color}>{STATUS[s]?.label ?? s}</Tag>,
          },
          {
            title: '操作',
            width: 220,
            render: (_, r) => (
              <Space>
                {r.status === 'draft' && has('quote:send') && (
                  <Button size="small" onClick={() => void act(r.id, 'sent')}>
                    发出
                  </Button>
                )}
                {r.status === 'sent' && has('quote:decide') && (
                  <>
                    <Button size="small" type="primary" ghost onClick={() => void act(r.id, 'won')}>
                      中标
                    </Button>
                    <Button size="small" danger onClick={() => void act(r.id, 'lost')}>
                      未中标
                    </Button>
                  </>
                )}
                {r.status === 'won' && !r.convertedContractId && has('quote:convert') && (
                  <Button size="small" type="primary" onClick={() => nav('/biz/contract')}>
                    转合同
                  </Button>
                )}
                {r.convertedContractId && <Tag color="green">已转合同</Tag>}
              </Space>
            ),
          },
        ]}
      />

      <Drawer
        title="报价登记（FR-M02-001）"
        width={480}
        open={open}
        onClose={() => setOpen(false)}
        extra={
          <Button type="primary" onClick={() => void submit()}>
            提交
          </Button>
        }
      >
        <Form form={form} layout="vertical" onValuesChange={onValuesChange}>
          <Form.Item name="partyId" label="客户主体（BR-08 下拉）" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={options.parties.map((p) => ({ value: p.id, label: p.name }))} />
          </Form.Item>
          <Form.Item name="productLines" label="产品线" rules={[{ required: true }]}>
            <Select
              mode="multiple"
              options={[
                { value: 'climber', label: '免爬器' },
                { value: 'lift', label: '升降机' },
                { value: 'ladder', label: '爬梯' },
                { value: 'rail', label: '导轨' },
                { value: 'video', label: '视频监控' },
                { value: 'fire', label: '自动消防' },
              ]}
            />
          </Form.Item>
          {hints.length > 0 && (
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 16 }}
              message="多头报价提示（FR-M02-002）"
              description={`同客户同产品线已有 ${hints.length} 笔未失效报价：${hints.map((h) => `${h.quotationCode}（${(Number(h.totalCents) / 1_000_000).toFixed(1)} 万，${STATUS[h.status]?.label}）`).join('；')}`}
            />
          )}
          <Form.Item name="qty" label="数量" rules={[{ required: true }]}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="unitPriceCents"
            label="单价（分）"
            rules={[{ required: true }]}
            extra="BR-10：金额以分存储（50 万元 = 50000000 分）"
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="validUntil" label="有效期至">
            <Input placeholder="如 2026-12-31" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  );
}
