import { useCallback, useEffect, useState } from 'react';
import {
  Button, Card, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, App, Descriptions, Progress,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { api, toastError, ApiError } from '../api/client';
import { useAuth } from '../stores/auth';
import { BlockCodeAlert } from '../components/BlockCodeAlert';

interface ProjectRow {
  id: string;
  projectCode: string;
  standardName: string;
  shortName: string;
  status: string;
  orderType: string;
  unitCount: number;
  pendingFlags: string[];
}
interface NamePreview {
  standardName: string;
  warnOwnerMissing: boolean;
  codeSuggestion: string;
}
interface Option { id: string; name?: string; fullName?: string; entityCode?: string; partyCode?: string; }

const STATUS_COLOR: Record<string, string> = {
  pending_farm: 'orange',
  pending_exec: 'blue',
  executing: 'geekblue',
  shipped: 'cyan',
  accepted: 'green',
  warranty: 'purple',
  closed: 'default',
};

/** 07 册 P1 列表族：项目档案（全系统锚点）+ 建档向导（名称生成器 4 进 3 出） */
export default function ProjectList() {
  const nav = useNavigate();
  const { message } = App.useApp();
  const has = useAuth((s) => s.has);
  const [rows, setRows] = useState<ProjectRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const [preview, setPreview] = useState<NamePreview | null>(null);
  const [dupErr, setDupErr] = useState<unknown>(null);
  const [options, setOptions] = useState<{ farms: Option[]; parties: Option[]; entities: Option[] }>({
    farms: [],
    parties: [],
    entities: [],
  });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const d = await api<{ list: ProjectRow[]; total: number }>('get', '/api/v1/mdm/projects', undefined, {
        page,
        pageSize: 20,
        keyword: keyword || undefined,
        status,
      });
      setRows(d.list);
      setTotal(d.total);
    } catch (e) {
      toastError(e);
    } finally {
      setLoading(false);
    }
  }, [page, keyword, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDrawer = async () => {
    setOpen(true);
    setDupErr(null);
    setPreview(null);
    try {
      const [farms, parties, entities] = await Promise.all([
        api<{ list: Option[] }>('get', '/api/v1/mdm/farms?pageSize=200'),
        api<{ list: Option[] }>('get', '/api/v1/mdm/parties?pageSize=200'),
        api<Option[]>('get', '/api/v1/mdm/entities'),
      ]);
      setOptions({ farms: farms.list, parties: parties.list, entities });
    } catch (e) {
      toastError(e);
    }
  };

  const onPreview = async () => {
    const v = form.getFieldsValue();
    if (!v.windFarmId) return;
    try {
      const p = await api<NamePreview>('post', '/api/v1/mdm/projects/name-preview', {
        windFarmId: v.windFarmId,
        ownerPartyId: v.ownerPartyId || undefined,
        phase: v.phase,
        orderType: v.orderType,
      });
      setPreview(p);
      form.setFieldValue('standardName', p.standardName);
    } catch (e) {
      toastError(e);
    }
  };

  const submit = async () => {
    const v = await form.validateFields();
    try {
      const created = await api<{ id: string; projectCode: string }>('post', '/api/v1/mdm/projects', v);
      message.success(`建档成功：${created.projectCode}（编码已生成不可修改，BR-09）`);
      setOpen(false);
      void load();
    } catch (e) {
      setDupErr(e);
      if (e instanceof ApiError && e.message.includes('三重查重')) {
        Modal.confirm({
          title: '三重查重命中，确认仍要新建？',
          content: '同名/同简称项目已存在，请先确认是否重复建档（FR-M01-010）。',
          okText: '确认新建（force）',
          cancelText: '返回检查',
          onOk: async () => {
            try {
              const created = await api<{ id: string; projectCode: string }>('post', '/api/v1/mdm/projects', { ...v, force: true });
              message.success(`建档成功：${created.projectCode}`);
              setOpen(false);
              void load();
            } catch (e2) {
              toastError(e2);
            }
          },
        });
      } else {
        toastError(e);
      }
    }
  };

  return (
    <Card
      title="项目档案（全系统锚点）"
      extra={
        <Space>
          <Input.Search
            placeholder="编码/标准名/简称"
            allowClear
            onSearch={(v) => {
              setPage(1);
              setKeyword(v);
            }}
            style={{ width: 220 }}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 140 }}
            options={Object.entries({
              pending_farm: '待关联风场',
              pending_exec: '签约待执行',
              executing: '执行中',
              shipped: '已发货',
              accepted: '已验收',
              warranty: '质保期中',
              closed: '已关闭',
            }).map(([value, label]) => ({ value, label }))}
            onChange={(v) => {
              setPage(1);
              setStatus(v);
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load()} />
          {has('mdm:project:create') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => void openDrawer()}>
              项目建档
            </Button>
          )}
        </Space>
      }
    >
      <Table<ProjectRow>
        rowKey="id"
        size="middle"
        loading={loading}
        dataSource={rows}
        pagination={{ current: page, total, pageSize: 20, onChange: setPage, showTotal: (t) => `共 ${t} 条` }}
        onRow={(r) => ({ onClick: () => nav(`/project/${r.id}`), style: { cursor: 'pointer' } })}
        columns={[
          { title: '项目编码', dataIndex: 'projectCode', width: 130, fixed: 'left' },
          { title: '标准名', dataIndex: 'standardName', ellipsis: true },
          { title: '简称', dataIndex: 'shortName', width: 110 },
          {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (s: string) => <Tag color={STATUS_COLOR[s] ?? 'default'}>{s}</Tag>,
          },
          { title: '订单类型', dataIndex: 'orderType', width: 120 },
          { title: '台数', dataIndex: 'unitCount', width: 70, align: 'right' },
          {
            title: '待补全',
            dataIndex: 'pendingFlags',
            width: 110,
            render: (flags: string[]) =>
              flags.length ? flags.map((f) => <Tag key={f} color="orange">{f === 'pending_farm' ? '待关联风场' : f === 'pending_owner' ? '待补业主' : f}</Tag>) : '—',
          },
        ]}
      />

      <Drawer
        title="项目建档（FR-M01-008 关键 12 项）"
        width={560}
        open={open}
        onClose={() => setOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void submit()}>
              提交建档
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Space.Compact block>
            <Form.Item name="windFarmId" label="风场（BR-08 下拉）" rules={[{ required: true }]} style={{ flex: 1 }}>
              <Select
                showSearch
                optionFilterProp="label"
                options={options.farms.map((f) => ({ value: f.id, label: f.fullName ?? '' }))}
                onChange={() => void onPreview()}
                placeholder="选择风场（无则先去风场库新建）"
              />
            </Form.Item>
            <Form.Item name="phase" label="期次" style={{ width: 120 }}>
              <Input placeholder="如：二期" onBlur={() => void onPreview()} />
            </Form.Item>
          </Space.Compact>
          <Form.Item name="ownerPartyId" label="业主主体">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={options.parties.map((p) => ({ value: p.id, label: p.name ?? '' }))}
              onChange={() => void onPreview()}
            />
          </Form.Item>
          <Form.Item name="orderType" label="订单类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'owner_retrofit', label: '业主技改（质保默认 12 月）' },
                { value: 'oem_initial', label: '整机厂初装（质保默认 60 月）' },
                { value: 'spare', label: '备件' },
                { value: 'inspection', label: '定检服务' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="standardName"
            label="标准名（生成器自动，可改）"
            rules={[{ required: true }]}
            extra={preview?.warnOwnerMissing ? '⚠️ 输入含地名未含业主名，请确认业主（FR-M01-009 红色提醒）' : preview ? `编码建议：${preview.codeSuggestion}` : undefined}
          >
            <Input />
          </Form.Item>
          <Form.Item name="shortName" label="常用简称（3—6 字，全公司唯一）" rules={[{ required: true, min: 2, max: 8 }]}>
            <Input placeholder="如：姚安二期" />
          </Form.Item>
          <Form.Item name="legalEntityId" label="我方签约主体（两司分账）" rules={[{ required: true }]}>
            <Select options={options.entities.map((e) => ({ value: e.id, label: `${e.entityCode} ${e.name ?? ''}` }))} />
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
          <Form.Item name="unitCount" label="台数/套数" rules={[{ required: true }]}>
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="contractAmountCents"
            label="合同金额（分，BR-10 整数分）"
            extra="录入口径提示：1 万元 = 1000000 分"
          >
            <InputNumber min={0} precision={0} style={{ width: '100%' }} placeholder="如 634100000" />
          </Form.Item>
          {dupErr !== null && <BlockCodeAlert error={dupErr} />}
        </Form>
      </Drawer>
    </Card>
  );
}

export function ProjectFieldLabels() {
  return <Descriptions size="small" column={2} items={[]} />;
}

export function CompletenessBar({ percent }: { percent: number }) {
  return <Progress percent={percent} size="small" />;
}
