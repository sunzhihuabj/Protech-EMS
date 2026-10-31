import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Descriptions, Input, Select, Space, Table, Tag, App, Alert } from 'antd';
import { ScanOutlined, ReloadOutlined } from '@ant-design/icons';
import { api, toastError, ApiError } from '../api/client';
import { BlockCodeAlert } from '../components/BlockCodeAlert';

interface MoveRow {
  id: string;
  moveType: string;
  sourceType: string;
  sourceId: string;
  materialId: string;
  qty: number;
  tPlusStatus: string;
  movedAt: string;
}

const TPLUS: Record<string, { color: string; label: string }> = {
  pending: { color: 'orange', label: '待推送 T+' },
  pushed: { color: 'blue', label: '已推送待回执' },
  confirmed: { color: 'green', label: 'T+ 已导入' },
};

/** 07 册 3.4 出入库作业（PDA 优先）：选单据 → 逐项扫码 → 数量确认 → 提交过账 → T+ 队列状态 */
export default function StockMoveWork() {
  const { message } = App.useApp();
  const [mode, setMode] = useState<'in' | 'out'>('in');
  const [sourceId, setSourceId] = useState('');
  const [materialCode, setMaterialCode] = useState('');
  const [qty, setQty] = useState<string>('');
  const [batch, setBatch] = useState('');
  const [legalEntity, setLegalEntity] = useState('PRT');
  const [projectId, setProjectId] = useState('');
  const [projects, setProjects] = useState<Array<{ id: string; projectCode: string; standardName: string }>>([]);
  const [last, setLast] = useState<{ id: string; duplicated: boolean; materialCode: string; tPlusStatus: string } | null>(null);
  const [err, setErr] = useState<unknown>(null);
  const [moves, setMoves] = useState<MoveRow[]>([]);

  const loadMoves = useCallback(async () => {
    try {
      const d = await api<{ list: MoveRow[] }>('get', '/api/v1/stock-moves?pageSize=20');
      setMoves(d.list);
    } catch (e) {
      toastError(e);
    }
  }, []);

  useEffect(() => {
    void loadMoves();
    api<{ list: Array<{ id: string; projectCode: string; standardName: string }> }>('get', '/api/v1/mdm/projects?pageSize=50')
      .then((d) => setProjects(d.list))
      .catch(() => undefined);
  }, [loadMoves]);

  const submit = async () => {
    setErr(null);
    try {
      const r = await api<{ id: string; duplicated: boolean; materialCode: string; tPlusStatus: string }>('post', '/api/v1/stock-moves', {
        moveType: mode,
        sourceType: mode === 'in' ? 'arrival' : 'ship',
        sourceId,
        materialCode: materialCode.trim(),
        qty: Number(qty),
        batch: batch || undefined,
        legalEntity,
        projectId: mode === 'out' && projectId ? projectId : undefined,
        // 幂等键：单据行+批号+数量（断网重传去重，11 册 §5.2）
        idempotencyKey: `${mode === 'in' ? 'Arrival' : 'Ship'}-${sourceId}-${materialCode.trim()}-${batch || 'NOBATCH'}-${qty}`,
      });
      setLast(r);
      message.success(r.duplicated ? '重复扫码已自动去重（幂等）' : `过账成功：${r.materialCode}`);
      setMaterialCode('');
      setQty('');
      void loadMoves();
    } catch (e) {
      setErr(e);
      toastError(e);
    }
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Card
        size="small"
        title="扫码出入库作业（源头单据驱动）"
        extra={
          <Space>
            <Select value={mode} style={{ width: 140 }} onChange={(v) => setMode(v)}
              options={[{ value: 'in', label: '入库（到货单）' }, { value: 'out', label: '出库（发货单）' }]} />
            <Button icon={<ReloadOutlined />} onClick={() => void loadMoves()} />
          </Space>
        }
      >
        <Descriptions size="small" column={2} style={{ marginBottom: 12 }}>
          <Descriptions.Item label="① 源头单据号">
            <Input
              style={{ width: 220 }}
              placeholder={mode === 'in' ? '采购单号 PO…（无单据不可入库）' : '发货单号…'}
              value={sourceId}
              onChange={(e) => setSourceId(e.target.value)}
            />
          </Descriptions.Item>
          <Descriptions.Item label="② 物料码（扫码输入）">
            <Input
              style={{ width: 220 }}
              placeholder="主码或旧码（映射自动换主码）"
              value={materialCode}
              onChange={(e) => setMaterialCode(e.target.value)}
              onPressEnter={() => void submit()}
              prefix={<ScanOutlined />}
            />
          </Descriptions.Item>
          <Descriptions.Item label="③ 数量">
            <Input style={{ width: 140 }} placeholder="默认单据数量" value={qty} onChange={(e) => setQty(e.target.value)} />
          </Descriptions.Item>
          <Descriptions.Item label="批次 / 法人">
            <Space>
              <Input style={{ width: 120 }} value={batch} onChange={(e) => setBatch(e.target.value)} placeholder="批次" />
              <Select value={legalEntity} style={{ width: 90 }} onChange={setLegalEntity}
                options={[{ value: 'PRT', label: 'PRT' }, { value: 'TLD', label: 'TLD' }]} />
            </Space>
          </Descriptions.Item>
          {mode === 'out' && (
            <Descriptions.Item label="项目码（BR-03 发货必挂）">
              <Select
                showSearch
                optionFilterProp="label"
                style={{ width: 300 }}
                allowClear
                placeholder="选择项目（BR-03：无项目编码不发货）"
                value={projectId || undefined}
                onChange={(v) => setProjectId(v ?? '')}
                options={projects.map((p) => ({ value: p.id, label: `${p.projectCode} ${p.standardName}` }))}
              />
            </Descriptions.Item>
          )}
        </Descriptions>
        <Space>
          <Button type="primary" size="large" icon={<ScanOutlined />} disabled={!sourceId || !materialCode || !qty} onClick={() => void submit()}>
            提交过账
          </Button>
          {last && (
            <Alert
              type={last.duplicated ? 'warning' : 'success'}
              showIcon
              message={last.duplicated ? '重复提交已去重' : `已过账 ${last.materialCode} → T+ 队列：${TPLUS[last.tPlusStatus]?.label}`}
            />
          )}
        </Space>
        {err !== null && <BlockCodeAlert error={err} />}
        {err instanceof ApiError && err.blockCode !== 'BR-03' && err.message.includes("映射表") && (
          <Alert type="warning" showIcon style={{ marginTop: 8 }} message="旧码未归并：已记录治理线索（FR-M06-021），请先在数据治理完成归并" />
        )}
      </Card>

      <Card size="small" title="最近流水（T+ 推送队列状态 FR-M06-007）">
        <Table<MoveRow>
          rowKey="id"
          size="small"
          dataSource={moves}
          pagination={{ pageSize: 10 }}
          columns={[
            { title: '方向', dataIndex: 'moveType', width: 70, render: (v: string) => <Tag color={v === 'in' ? 'green' : 'red'}>{v === 'in' ? '入库' : '出库'}</Tag> },
            { title: '来源', dataIndex: 'sourceType', width: 80 },
            { title: '单据号', dataIndex: 'sourceId', width: 150 },
            { title: '数量', dataIndex: 'qty', width: 90, align: 'right' },
            {
              title: 'T+ 队列',
              dataIndex: 'tPlusStatus',
              width: 130,
              render: (s: string) => <Tag color={TPLUS[s]?.color}>{TPLUS[s]?.label ?? s}</Tag>,
            },
            { title: '时间', dataIndex: 'movedAt', width: 160, render: (v: string) => new Date(v).toLocaleString('zh-CN') },
          ]}
        />
      </Card>
    </Space>
  );
}
