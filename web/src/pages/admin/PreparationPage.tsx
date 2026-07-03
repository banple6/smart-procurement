import { DownloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Form, Input, Select, Table } from 'antd';
import { useState } from 'react';
import { ledgerApi } from '@/api/ledger';
import { PageHeader } from '@/components/Page';

export function PreparationPage() {
  const [params, setParams] = useState({ scope: 'pending_preparation', page: 1, page_size: 50 });
  const { data, isFetching } = useQuery({ queryKey: ['admin', 'preparation', params], queryFn: () => ledgerApi.preparation(params) });
  return (
    <div className="page-shell">
      <PageHeader title="今日备货" description="按食材汇总今日需要备货的数量" extra={<Button icon={<DownloadOutlined />} onClick={() => ledgerApi.exportPreparation(params)}>导出 Excel</Button>} />
      <div className="filter-panel">
        <Form layout="inline">
          <Form.Item label="日期"><Input type="date" onChange={(e) => setParams((p) => ({ ...p, business_date: e.target.value }))} /></Form.Item>
          <Form.Item label="范围">
            <Select
              value={params.scope}
              style={{ width: 150 }}
              onChange={(scope) => setParams((p) => ({ ...p, scope }))}
              options={[
                { value: 'pending_preparation', label: '待备货' },
                { value: 'active', label: '全部有效' },
                { value: 'shipped', label: '已发货' },
              ]}
            />
          </Form.Item>
        </Form>
      </div>
      <Table
        rowKey="product_id"
        loading={isFetching}
        dataSource={data?.items || []}
        pagination={{ total: data?.total || 0, pageSize: params.page_size, onChange: (page) => setParams((p) => ({ ...p, page })) }}
        columns={[
          { title: '商品名称', dataIndex: 'product_name' },
          { title: '规格', dataIndex: 'spec' },
          { title: '单位', dataIndex: 'unit' },
          { title: '申请数量', dataIndex: 'requested_quantity' },
          { title: '实际备货数量', dataIndex: 'actual_quantity' },
          { title: '涉及单位数', dataIndex: 'unit_count' },
          { title: '订单数', dataIndex: 'order_count' },
        ]}
      />
    </div>
  );
}
