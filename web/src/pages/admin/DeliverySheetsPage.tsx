import { DownloadOutlined, PrinterOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, Form, Input, Select, Space, Table } from 'antd';
import { useState } from 'react';
import { ledgerApi } from '@/api/ledger';
import { unitsApi } from '@/api/units';
import { PageHeader, PrintHeader, StatusTag } from '@/components/Page';
import { orderStatusText } from '@/utils/status';

export function DeliverySheetsPage() {
  const [params, setParams] = useState<{ business_date?: string; status?: string; unit_id?: string }>({});
  const { data } = useQuery({ queryKey: ['admin', 'delivery', params], queryFn: () => ledgerApi.deliverySheets(params) });
  const { data: units = [] } = useQuery({ queryKey: ['admin', 'units'], queryFn: unitsApi.list });
  return (
    <div className="page-shell">
      <PrintHeader title="单位配送单" />
      <PageHeader
        title="单位配送"
        description="按单位分组查看配送明细，支持导出和打印"
        extra={[
          <Button key="print" icon={<PrinterOutlined />} onClick={() => window.print()}>打印</Button>,
          <Button key="export" icon={<DownloadOutlined />} onClick={() => ledgerApi.exportDeliverySheets(params)}>导出 Excel</Button>,
        ]}
      />
      <div className="filter-panel">
        <Form layout="inline">
          <Form.Item label="日期"><Input type="date" onChange={(e) => setParams((p) => ({ ...p, business_date: e.target.value }))} /></Form.Item>
          <Form.Item label="状态"><Select allowClear style={{ width: 140 }} onChange={(status) => setParams((p) => ({ ...p, status }))} options={Object.entries(orderStatusText).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item label="单位"><Select allowClear showSearch optionFilterProp="label" style={{ width: 180 }} onChange={(unit_id) => setParams((p) => ({ ...p, unit_id }))} options={units.map((unit) => ({ value: unit.id, label: unit.unit_name }))} /></Form.Item>
        </Form>
      </div>
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        {(data?.units || []).map((unit: any) => (
          <Card key={unit.unit_id} title={`${unit.unit_name} · ${unit.delivery_point}`}>
            {(unit.orders || []).map((order: any) => (
              <Card key={order.order_id} type="inner" title={<Space>{order.order_no}<StatusTag status={order.status} /></Space>} style={{ marginBottom: 10 }}>
                <Table
                  rowKey="item_id"
                  size="small"
                  pagination={false}
                  dataSource={order.items}
                  columns={[
                    { title: '商品', dataIndex: 'product_name' },
                    { title: '规格', dataIndex: 'spec' },
                    { title: '申请数量', dataIndex: 'requested_quantity' },
                    { title: '实际供应数量', dataIndex: 'actual_quantity' },
                    { title: '备注', dataIndex: 'adjustment_reason' },
                  ]}
                />
              </Card>
            ))}
          </Card>
        ))}
      </Space>
    </div>
  );
}
