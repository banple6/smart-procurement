import { useMutation } from '@tanstack/react-query';
import { App as AntApp, Button, Card, Descriptions, Input, Modal, Space, Table } from 'antd';
import { useNavigate } from 'react-router-dom';
import { ordersApi } from '@/api/orders';
import { queryClient } from '@/app/queryClient';
import { PageHeader } from '@/components/Page';
import { useAuth } from '@/auth/AuthContext';
import { useCart } from '@/unit/CartContext';
import { formatMoney, lineSubtotal } from '@/utils/money';

export function UnitCartPage() {
  const { user } = useAuth();
  const cart = useCart();
  const navigate = useNavigate();
  const { message } = AntApp.useApp();
  const mutation = useMutation({
    mutationFn: () => ordersApi.create({ note: cart.note, client_request_id: crypto.randomUUID(), items: cart.lines.map((line) => ({ product_id: line.product.id, quantity: String(line.quantity) })) }),
    onSuccess: async (order) => {
      cart.clear();
      await queryClient.invalidateQueries({ queryKey: ['unit', 'orders'] });
      await queryClient.invalidateQueries({ queryKey: ['products'] });
      Modal.success({ title: '提交成功', content: `订单编号：${order.order_no}\n当前状态：待接单`, okText: '查看订单', onOk: () => navigate('/unit/orders') });
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '提交失败，请稍后重试'),
  });
  const confirmSubmit = () => {
    Modal.confirm({
      title: '确认提交这份采购单吗？',
      content: `共 ${cart.lines.length} 种食材，合计 ${formatMoney(cart.totalCents)}`,
      okText: '提交订单',
      cancelText: '取消',
      onOk: () => mutation.mutateAsync(),
    });
  };
  return (
    <div className="page-shell">
      <PageHeader title="采购清单" description="确认数量、配送点和备注后提交订单" />
      <Space direction="vertical" style={{ width: '100%' }} size={14}>
        <Card>
          <Table
            rowKey={(line) => line.product.id}
            dataSource={cart.lines}
            pagination={false}
            columns={[
              { title: '商品', render: (_, line) => line.product.name },
              { title: '数量', dataIndex: 'quantity' },
              { title: '单价', render: (_, line) => formatMoney(line.product.price_cents) },
              { title: '小计', render: (_, line) => formatMoney(lineSubtotal(line.product.price_cents, line.quantity)) },
              { title: '操作', render: (_, line) => <Button onClick={() => cart.remove(line.product.id)}>移除</Button> },
            ]}
          />
        </Card>
        <Card>
          <Descriptions column={1} bordered>
            <Descriptions.Item label="默认配送点">{user?.default_delivery_point || '未设置'}</Descriptions.Item>
            <Descriptions.Item label="订单合计">{formatMoney(cart.totalCents)}</Descriptions.Item>
          </Descriptions>
          <Input.TextArea rows={3} placeholder="备注（可选）" value={cart.note} onChange={(event) => cart.setNote(event.target.value)} style={{ marginTop: 12 }} />
        </Card>
        <Button type="primary" size="large" block disabled={!cart.lines.length} loading={mutation.isPending} onClick={confirmSubmit}>提交订单</Button>
      </Space>
    </div>
  );
}
