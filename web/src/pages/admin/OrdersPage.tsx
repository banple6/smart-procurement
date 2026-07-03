import { CameraOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App as AntApp, Button, Card, Descriptions, Drawer, Form, Input, Select, Space, Table, Upload } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ordersApi, type OrderFilters } from '@/api/orders';
import { unitsApi } from '@/api/units';
import { queryClient } from '@/app/queryClient';
import { MobileCardList, PageHeader, StatusTag } from '@/components/Page';
import type { Order, OrderItem } from '@/types/domain';
import { formatMoney } from '@/utils/money';
import { nextAdminOrderAction, orderStatusText } from '@/utils/status';

function ShipPanel({ order, onDone }: { order: Order; onDone: () => void }) {
  const { message } = AntApp.useApp();
  const [files, setFiles] = useState<File[]>([]);
  const [note, setNote] = useState(order.shipping_note || '');
  const mutation = useMutation({
    mutationFn: () => ordersApi.ship(order.id, { files, note }),
    onSuccess: async () => {
      message.success('确认发货成功');
      await queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
      await queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      onDone();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '发货失败'),
  });
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Upload
        accept="image/*"
        listType="picture-card"
        maxCount={3}
        beforeUpload={(file) => {
          setFiles((current) => [...current, file].slice(0, 3));
          return false;
        }}
        onRemove={(file) => setFiles((current) => current.filter((item) => item.name !== file.name))}
      >
        {files.length >= 3 ? null : '选择照片'}
      </Upload>
      <Input.TextArea placeholder="发货备注" value={note} onChange={(event) => setNote(event.target.value)} rows={3} />
      <Button type="primary" icon={<CameraOutlined />} loading={mutation.isPending} disabled={files.length === 0} onClick={() => mutation.mutate()}>
        上传照片并确认发货
      </Button>
    </Space>
  );
}

function OrderDetail({ order, onRefresh }: { order: Order; onRefresh: () => void }) {
  const { message, modal } = AntApp.useApp();
  const action = nextAdminOrderAction(order.status);
  const [shipOpen, setShipOpen] = useState(false);
  const [adjusting, setAdjusting] = useState<string | null>(null);

  const doNext = () => {
    if (!action) return;
    if (action.ship) {
      setShipOpen(true);
      return;
    }
    modal.confirm({
      title: `确认${action.label}吗？`,
      okText: action.label,
      cancelText: '取消',
      onOk: async () => {
        await ordersApi.adminStatus(order.id, action.nextStatus!);
        message.success('订单状态已更新');
        await queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
        await queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
        onRefresh();
      },
    });
  };

  const adjustItem = (item: OrderItem) => {
    let actual_quantity = item.actual_quantity || item.quantity;
    let reason = '';
    modal.confirm({
      title: `调整数量：${item.product_name_snapshot}`,
      content: (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Input defaultValue={actual_quantity} onChange={(event) => (actual_quantity = event.target.value)} />
          <Input placeholder="调整原因" onChange={(event) => (reason = event.target.value)} />
        </Space>
      ),
      okText: '保存数量',
      cancelText: '取消',
      onOk: async () => {
        setAdjusting(item.id);
        try {
          await ordersApi.adjustItem(order.id, item.id, { actual_quantity, reason });
          message.success('数量已更新');
          onRefresh();
        } finally {
          setAdjusting(null);
        }
      },
    });
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Descriptions bordered column={1}>
        <Descriptions.Item label="订单编号">{order.order_no}</Descriptions.Item>
        <Descriptions.Item label="单位">{order.unit_name_snapshot}</Descriptions.Item>
        <Descriptions.Item label="配送点">{order.delivery_point_snapshot}</Descriptions.Item>
        <Descriptions.Item label="状态"><StatusTag status={order.status} /></Descriptions.Item>
        <Descriptions.Item label="合计">{formatMoney(order.total_cents)}</Descriptions.Item>
        <Descriptions.Item label="备注">{order.note || '无'}</Descriptions.Item>
      </Descriptions>
      {action ? <Button type="primary" size="large" block onClick={doNext}>{action.label}</Button> : null}
      {shipOpen ? <Card title="发货凭证"><ShipPanel order={order} onDone={() => { setShipOpen(false); onRefresh(); }} /></Card> : null}
      <Table
        rowKey="id"
        pagination={false}
        dataSource={order.items || []}
        columns={[
          { title: '食材', dataIndex: 'product_name_snapshot' },
          { title: '规格', dataIndex: 'spec_snapshot' },
          { title: '申请数量', dataIndex: 'quantity' },
          { title: '实际供应', render: (_, item) => item.actual_quantity || item.quantity },
          { title: '单价', dataIndex: 'price_cents_snapshot', render: formatMoney },
          { title: '小计', dataIndex: 'subtotal_cents', render: formatMoney },
          { title: '操作', render: (_, item) => <Button loading={adjusting === item.id} onClick={() => adjustItem(item)}>调整数量</Button> },
        ]}
      />
      {order.shipping_photos?.length ? (
        <Card title="发货照片">
          <Space wrap>
            {order.shipping_photos.map((photo) => <img key={photo.id} src={photo.thumbnail_url || photo.url} alt="发货照片" style={{ width: 96, height: 96, objectFit: 'cover', borderRadius: 6 }} />)}
          </Space>
        </Card>
      ) : null}
    </Space>
  );
}

export function OrdersPage() {
  const [params] = useSearchParams();
  const [filters, setFilters] = useState<OrderFilters>({
    page: 1,
    page_size: 20,
    status: params.get('status') || undefined,
    unit_id: params.get('unit_id') || undefined,
    order_no: params.get('order_no') || undefined,
  });
  const [active, setActive] = useState<Order | null>(null);
  const { data, isFetching, refetch } = useQuery({ queryKey: ['admin', 'orders', filters], queryFn: () => ordersApi.listAdmin(filters), refetchInterval: 45_000 });
  const { data: units = [] } = useQuery({ queryKey: ['admin', 'units'], queryFn: unitsApi.list });

  const columns: ColumnsType<Order> = [
    { title: '订单编号', dataIndex: 'order_no' },
    { title: '单位', dataIndex: 'unit_name_snapshot' },
    { title: '配送点', dataIndex: 'delivery_point_snapshot' },
    { title: '状态', dataIndex: 'status', render: (status) => <StatusTag status={status} /> },
    { title: '金额', dataIndex: 'total_cents', render: formatMoney },
    { title: '创建时间', dataIndex: 'created_at' },
    { title: '操作', render: (_, record) => <Button onClick={() => setActive(record)}>查看详情</Button> },
  ];

  return (
    <div className="page-shell">
      <PageHeader title="订单管理" description="处理 App 和网站提交的采购订单" extra={<Button icon={<ReloadOutlined />} onClick={() => refetch()}>刷新</Button>} />
      <div className="filter-panel">
        <Form layout="inline">
          <Form.Item label="状态">
            <Select allowClear style={{ width: 150 }} value={filters.status} onChange={(status) => setFilters((f) => ({ ...f, status, page: 1 }))} options={Object.entries(orderStatusText).map(([value, label]) => ({ value, label }))} />
          </Form.Item>
          <Form.Item label="单位">
            <Select allowClear showSearch style={{ width: 190 }} optionFilterProp="label" value={filters.unit_id} onChange={(unit_id) => setFilters((f) => ({ ...f, unit_id, page: 1 }))} options={units.map((unit) => ({ value: unit.id, label: unit.unit_name }))} />
          </Form.Item>
          <Form.Item label="订单编号">
            <Input allowClear value={filters.order_no} onChange={(e) => setFilters((f) => ({ ...f, order_no: e.target.value, page: 1 }))} />
          </Form.Item>
        </Form>
      </div>
      <Table
        className="desktop-table"
        rowKey="id"
        loading={isFetching}
        dataSource={data?.items || []}
        columns={columns}
        pagination={{ current: data?.page || 1, total: data?.total || 0, pageSize: data?.page_size || 20, onChange: (page) => setFilters((f) => ({ ...f, page })) }}
      />
      <MobileCardList
        items={data?.items || []}
        render={(order) => (
          <Card key={order.id}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <strong>{order.order_no}</strong>
              <span>{order.unit_name_snapshot}</span>
              <StatusTag status={order.status} />
              <span>{formatMoney(order.total_cents)}</span>
              <Button onClick={() => setActive(order)}>查看详情</Button>
            </Space>
          </Card>
        )}
      />
      <Drawer width={720} title="订单详情" open={Boolean(active)} onClose={() => setActive(null)} destroyOnClose>
        {active ? <OrderDetail order={active} onRefresh={() => ordersApi.detail(active.id, true).then(setActive)} /> : null}
      </Drawer>
    </div>
  );
}
