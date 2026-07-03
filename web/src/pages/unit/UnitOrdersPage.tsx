import { useMutation, useQuery } from '@tanstack/react-query';
import { App as AntApp, Button, Card, Drawer, Form, Input, Select, Space, Table, Upload } from 'antd';
import { useState } from 'react';
import { ordersApi, type OrderFilters } from '@/api/orders';
import { queryClient } from '@/app/queryClient';
import { PageHeader, StatusTag } from '@/components/Page';
import type { Order } from '@/types/domain';
import { formatMoney } from '@/utils/money';
import { orderStatusText } from '@/utils/status';

function ReceiptIssueForm({ order, onDone }: { order: Order; onDone: () => void }) {
  const { message } = AntApp.useApp();
  const [issueType, setIssueType] = useState('数量不符');
  const [description, setDescription] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const mutation = useMutation({
    mutationFn: () => ordersApi.createReceiptIssue(order.id, { issue_type: issueType, description, files }),
    onSuccess: async () => {
      message.success('收货问题已提交');
      await queryClient.invalidateQueries({ queryKey: ['unit', 'orders'] });
      onDone();
    },
  });
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Select value={issueType} onChange={setIssueType} options={['数量不符', '质量问题', '未收到', '其他'].map((value) => ({ value, label: value }))} />
      <Input.TextArea rows={3} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="问题说明" />
      <Upload accept="image/*" capture="environment" listType="picture-card" beforeUpload={(file) => { setFiles((current) => [...current, file].slice(0, 3)); return false; }} maxCount={3}>
        {files.length >= 3 ? null : '选择照片'}
      </Upload>
      <Button type="primary" loading={mutation.isPending} onClick={() => mutation.mutate()}>提交收货问题</Button>
    </Space>
  );
}

export function UnitOrdersPage() {
  const { message, modal } = AntApp.useApp();
  const [filters, setFilters] = useState<OrderFilters>({ page: 1, page_size: 20 });
  const [active, setActive] = useState<Order | null>(null);
  const [issueOrder, setIssueOrder] = useState<Order | null>(null);
  const { data, isFetching } = useQuery({ queryKey: ['unit', 'orders', filters], queryFn: () => ordersApi.listMine(filters), refetchInterval: 45_000 });
  const confirmReceipt = (order: Order) => {
    modal.confirm({
      title: '确认已收到这份订单吗？',
      okText: '确认收货',
      cancelText: '取消',
      onOk: async () => {
        await ordersApi.confirmReceipt(order.id);
        message.success('已确认收货');
        await queryClient.invalidateQueries({ queryKey: ['unit', 'orders'] });
      },
    });
  };
  return (
    <div className="page-shell">
      <PageHeader title="我的订单" description="查看订单状态、发货照片和收货反馈" />
      <div className="filter-panel">
        <Form layout="inline">
          <Form.Item label="状态"><Select allowClear style={{ width: 140 }} onChange={(status) => setFilters((f) => ({ ...f, status }))} options={Object.entries(orderStatusText).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item label="开始日期"><Input type="date" onChange={(e) => setFilters((f) => ({ ...f, start_date: e.target.value }))} /></Form.Item>
          <Form.Item label="结束日期"><Input type="date" onChange={(e) => setFilters((f) => ({ ...f, end_date: e.target.value }))} /></Form.Item>
        </Form>
      </div>
      <Table
        rowKey="id"
        loading={isFetching}
        dataSource={data?.items || []}
        pagination={{ total: data?.total || 0, current: data?.page || 1, pageSize: data?.page_size || 20, onChange: (page) => setFilters((f) => ({ ...f, page })) }}
        columns={[
          { title: '订单编号', dataIndex: 'order_no' },
          { title: '状态', dataIndex: 'status', render: (status) => <StatusTag status={status} /> },
          { title: '金额', dataIndex: 'total_cents', render: formatMoney },
          { title: '创建时间', dataIndex: 'created_at' },
          {
            title: '操作',
            render: (_, order) => (
              <Space wrap>
                <Button onClick={() => ordersApi.detail(order.id).then(setActive)}>详情</Button>
                {order.status === 'pending' ? <Button danger onClick={() => ordersApi.cancel(order.id).then(() => queryClient.invalidateQueries({ queryKey: ['unit', 'orders'] }))}>取消</Button> : null}
                {order.status === 'shipped' ? <Button type="primary" onClick={() => confirmReceipt(order)}>确认收货</Button> : null}
                {order.status === 'shipped' ? <Button onClick={() => setIssueOrder(order)}>收货问题</Button> : null}
              </Space>
            ),
          },
        ]}
      />
      <Drawer title="订单详情" open={Boolean(active)} onClose={() => setActive(null)} width={680}>
        {active ? (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Card title={active.order_no}>
              <p><StatusTag status={active.status} /> {formatMoney(active.total_cents)}</p>
              <p>配送点：{active.delivery_point_snapshot}</p>
              <p>备注：{active.note || '无'}</p>
            </Card>
            <Table rowKey="id" pagination={false} dataSource={active.items || []} columns={[{ title: '商品', dataIndex: 'product_name_snapshot' }, { title: '数量', dataIndex: 'quantity' }, { title: '实际供应', render: (_, item) => item.actual_quantity || item.quantity }, { title: '小计', dataIndex: 'subtotal_cents', render: formatMoney }]} />
            {active.shipping_photos?.length ? <Card title="发货照片"><Space wrap>{active.shipping_photos.map((photo) => <img key={photo.id} src={photo.thumbnail_url || photo.url} alt="发货照片" style={{ width: 96, height: 96, objectFit: 'cover' }} />)}</Space></Card> : null}
          </Space>
        ) : null}
      </Drawer>
      <Drawer title="提交收货问题" open={Boolean(issueOrder)} onClose={() => setIssueOrder(null)} width={420}>
        {issueOrder ? <ReceiptIssueForm order={issueOrder} onDone={() => setIssueOrder(null)} /> : null}
      </Drawer>
    </div>
  );
}
