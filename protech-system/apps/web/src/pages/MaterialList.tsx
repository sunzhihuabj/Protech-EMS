import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Input, Select, Space, Table, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { api, toastError } from '../api/client';

interface MaterialRow {
  id: string;
  materialCode: string;
  name: string;
  spec: string | null;
  unit: string;
  category: string | null;
  abcClass: string | null;
  legalEntityScope: string;
  status: string;
}

/** 07 册 P1 列表族：物料主数据（一码一物；旧码可溯在详情，S1 补全） */
export default function MaterialList() {
  const [rows, setRows] = useState<MaterialRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const d = await api<{ list: MaterialRow[]; total: number }>('get', '/api/v1/mdm/materials', undefined, {
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

  return (
    <Card
      title="物料主数据（一码一物）"
      extra={
        <Space>
          <Input.Search
            placeholder="编码/名称/规格"
            allowClear
            onSearch={(v) => {
              setPage(1);
              setKeyword(v);
            }}
            style={{ width: 220 }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void load()} />
        </Space>
      }
    >
      <Table<MaterialRow>
        rowKey="id"
        size="middle"
        loading={loading}
        dataSource={rows}
        pagination={{ current: page, total, pageSize: 20, onChange: setPage, showTotal: (t) => `共 ${t} 条` }}
        columns={[
          { title: '物料编码', dataIndex: 'materialCode', width: 150 },
          { title: '名称', dataIndex: 'name' },
          { title: '规格', dataIndex: 'spec' },
          { title: '单位', dataIndex: 'unit', width: 70 },
          { title: '类别', dataIndex: 'category', width: 100 },
          { title: 'ABC', dataIndex: 'abcClass', width: 60, render: (v) => (v ? <Tag>{v}</Tag> : '—') },
          { title: '主体范围', dataIndex: 'legalEntityScope', width: 90 },
          {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (s: string) => <Tag color={s === 'active' ? 'green' : s === 'frozen' ? 'orange' : 'default'}>{s}</Tag>,
          },
        ]}
      />
    </Card>
  );
}
