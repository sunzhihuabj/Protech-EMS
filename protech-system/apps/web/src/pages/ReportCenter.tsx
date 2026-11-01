import { useState } from 'react';
import { Button, Card, Select, Space, Table, App } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { api, toastError } from '../api/client';

const REPORTS = [
  { key: 'contracts', label: '合同台账' },
  { key: 'projects', label: '项目台账' },
  { key: 'stock-moves', label: '库存流水' },
  { key: 'receipts', label: '回款台账' },
];

/** 07 册 /report 报表中心（FR-M14-001/002/011：预置库+水印导出+AI 三句话解读） */
export default function ReportCenter() {
  const { message } = App.useApp();
  const [key, setKey] = useState('contracts');
  const [rows, setRows] = useState<Array<Record<string, string | number>>>([]);
  const [interpretation, setInterpretation] = useState('');
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    setInterpretation('');
    try {
      const d = await api<{ rows: Array<Record<string, string | number>> }>('get', `/api/v1/reports/${key}`);
      setRows(d.rows);
    } catch (e) {
      toastError(e);
    } finally {
      setLoading(false);
    }
  };

  const exportCsv = async () => {
    try {
      const d = await api<{ filename: string; content: string }>('get', `/api/v1/reports/${key}/csv`);
      const blob = new Blob(['\ufeff' + d.content], { type: 'text/csv;charset=utf-8' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = d.filename;
      a.click();
      message.success('已导出（每行含水印：生成人+时间 FR-M14-002）');
    } catch (e) {
      toastError(e);
    }
  };

  const interpret = async () => {
    try {
      const r = await api<{ interpretation: string }>('post', '/api/v1/ai/AI-11/run', { reportKey: key });
      setInterpretation(r.interpretation);
    } catch (e) {
      toastError(e);
    }
  };

  const columns = rows.length
    ? Object.keys(rows[0]).map((k) => ({ title: k, dataIndex: k, key: k, ellipsis: true }))
    : [];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Card
        size="small"
        title="报表中心（预置口径 12 册一致）"
        extra={
          <Space>
            <Select style={{ width: 160 }} value={key} onChange={setKey} options={REPORTS} />
            <Button onClick={() => void load()}>查询</Button>
            <Button icon={<DownloadOutlined />} onClick={() => void exportCsv()}>
              导出 CSV（水印）
            </Button>
            <Button type="primary" ghost onClick={() => void interpret()}>
              AI 三句话解读
            </Button>
          </Space>
        }
      >
        {interpretation && (
          <AlertInCard text={interpretation} />
        )}
        <Table
          rowKey={(_, i) => String(i)}
          size="small"
          loading={loading}
          dataSource={rows}
          columns={columns}
          scroll={{ x: 'max-content' }}
          pagination={{ pageSize: 15, showTotal: (t) => `共 ${t} 行` }}
        />
      </Card>
    </Space>
  );
}

function AlertInCard({ text }: { text: string }) {
  return (
    <div
      style={{
        background: '#f0f7ff',
        border: '1px solid #bee0ff',
        borderRadius: 8,
        padding: '8px 12px',
        marginBottom: 12,
        fontSize: 13,
      }}
    >
      🤖 {text}
    </div>
  );
}
