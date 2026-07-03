import { DownloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Form, Input, Select, Space, Table, Tabs } from 'antd';
import { useState } from 'react';
import { dashboardApi } from '@/api/dashboard';
import { ledgerApi, type LedgerFilters } from '@/api/ledger';
import { unitsApi } from '@/api/units';
import { PageHeader } from '@/components/Page';
import { formatMoney } from '@/utils/money';
import { orderStatusText } from '@/utils/status';

export function LedgerPage() {
  const [filters, setFilters] = useState<LedgerFilters>({});
  const { data = [], isFetching, refetch } = useQuery({ queryKey: ['admin', 'ledger', filters], queryFn: () => ledgerApi.list(filters) });
  const { data: summary = [] } = useQuery({ queryKey: ['admin', 'order-summary'], queryFn: dashboardApi.orderSummary });
  const { data: units = [] } = useQuery({ queryKey: ['admin', 'units'], queryFn: unitsApi.list });

  return (
    <div className="page-shell">
      <PageHeader title="采购台账" description="订单台账、商品汇总和 Excel 导出" />
      <div className="filter-panel">
        <Form layout="inline">
          <Form.Item label="开始日期"><Input type="date" onChange={(e) => setFilters((f) => ({ ...f, start_date: e.target.value }))} /></Form.Item>
          <Form.Item label="结束日期"><Input type="date" onChange={(e) => setFilters((f) => ({ ...f, end_date: e.target.value }))} /></Form.Item>
          <Form.Item label="单位"><Select allowClear showSearch optionFilterProp="label" style={{ width: 180 }} onChange={(unit_id) => setFilters((f) => ({ ...f, unit_id }))} options={units.map((unit) => ({ value: unit.id, label: unit.unit_name }))} /></Form.Item>
          <Form.Item label="状态"><Select allowClear style={{ width: 140 }} onChange={(status) => setFilters((f) => ({ ...f, status }))} options={Object.entries(orderStatusText).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item label="订单编号"><Input allowClear onChange={(e) => setFilters((f) => ({ ...f, order_no: e.target.value }))} /></Form.Item>
          <Space>
            <Button icon={<SearchOutlined />} onClick={() => refetch()}>查询</Button>
            <Button icon={<DownloadOutlined />} onClick={() => ledgerApi.exportLedger(filters)}>导出 Excel</Button>
          </Space>
        </Form>
      </div>
      <Tabs
        items={[
          {
            key: 'orders',
            label: '订单台账',
            children: (
              <Table
                rowKey={(row) => `${row.id}-${row.product_id}`}
                loading={isFetching}
                dataSource={data}
                scroll={{ x: 1200 }}
                columns={[
                  { title: '订单编号', dataIndex: 'order_no' },
                  { title: '单位', dataIndex: 'unit_name_snapshot' },
                  { title: '配送点', dataIndex: 'delivery_point_snapshot' },
                  { title: '商品', dataIndex: 'product_name_snapshot' },
                  { title: '规格', dataIndex: 'spec_snapshot' },
                  { title: '数量', dataIndex: 'quantity' },
                  { title: '单价', dataIndex: 'price_cents_snapshot', render: formatMoney },
                  { title: '小计', dataIndex: 'subtotal_cents', render: formatMoney },
                ]}
              />
            ),
          },
          {
            key: 'summary',
            label: '商品汇总',
            children: <Table rowKey="product_id" dataSource={summary} columns={[{ title: '商品', dataIndex: 'name' }, { title: '数量', dataIndex: 'quantity' }, { title: '单位', dataIndex: 'unit' }]} />,
          },
        ]}
      />
    </div>
  );
}
