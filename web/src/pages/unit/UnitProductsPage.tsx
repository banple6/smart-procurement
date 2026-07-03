import { ReloadOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Badge, Button, Card, Col, Form, Input, InputNumber, Row, Select, Space, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { productsApi } from '@/api/products';
import { useCart } from '@/unit/CartContext';
import { PageHeader, StatusTag } from '@/components/Page';
import type { Product } from '@/types/domain';
import { availableQuantity, numericQuantity } from '@/utils/inventory';
import { formatMoney } from '@/utils/money';

function ProductCard({ product }: { product: Product }) {
  const cart = useCart();
  const existing = cart.lines.find((line) => line.product.id === product.id)?.quantity || 0;
  const available = availableQuantity(product);
  const disabled = !product.active || Boolean(product.is_deleted) || product.supply_status === 'paused' || product.supply_status === 'off_shelf' || available <= 0;
  const min = numericQuantity(product.min_order_quantity) || 1;
  const step = numericQuantity(product.quantity_step) || 1;
  return (
    <Card>
      <Space direction="vertical" style={{ width: '100%' }} size={10}>
        {product.image_path ? <img src={product.image_path} alt={product.name} style={{ width: '100%', aspectRatio: '4 / 3', objectFit: 'cover', borderRadius: 6 }} /> : <div style={{ width: '100%', aspectRatio: '4 / 3', display: 'grid', placeItems: 'center', background: '#F3F6FB', borderRadius: 6 }}>暂无图片</div>}
        <Space align="start" style={{ justifyContent: 'space-between', width: '100%' }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>{product.name}</Typography.Title>
            <Typography.Text type="secondary">{product.spec}</Typography.Text>
          </div>
          <StatusTag status={available <= 0 ? 'off_shelf' : product.supply_status} />
        </Space>
        <div><strong>{formatMoney(product.price_cents)}</strong> / {product.unit}</div>
        <Typography.Text type={available <= 0 ? 'danger' : undefined}>{available <= 0 ? '库存不足' : `可用库存：${available} ${product.unit}`}</Typography.Text>
        <Space.Compact block>
          <Button disabled={existing <= 0} onClick={() => cart.setQuantity(product, existing - step)}>-</Button>
          <InputNumber value={existing} min={0} step={step} disabled={disabled} onChange={(value) => cart.setQuantity(product, Number(value || 0))} style={{ width: '100%' }} addonAfter={product.unit} />
          <Button disabled={disabled || existing + step > available} onClick={() => cart.setQuantity(product, existing > 0 ? existing + step : min)}>+</Button>
        </Space.Compact>
      </Space>
    </Card>
  );
}

export function UnitProductsPage() {
  const [q, setQ] = useState('');
  const [category, setCategory] = useState<string | undefined>();
  const navigate = useNavigate();
  const cart = useCart();
  const { data = [], isFetching, refetch } = useQuery({ queryKey: ['products', q, category], queryFn: () => productsApi.list({ q, category }) });
  const categories = useMemo(() => Array.from(new Set(data.map((item) => item.category))).filter(Boolean), [data]);
  return (
    <div className="page-shell">
      <PageHeader
        title="食材申领"
        description="选择食材后到采购清单提交订单"
        extra={[
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => refetch()}>刷新</Button>,
          <Badge key="cart" count={cart.lines.length}><Button type="primary" icon={<ShoppingCartOutlined />} onClick={() => navigate('/unit/cart')}>采购清单</Button></Badge>,
        ]}
      />
      <div className="filter-panel">
        <Form layout="inline">
          <Form.Item label="搜索"><Input allowClear placeholder="搜索食材名称" value={q} onChange={(event) => setQ(event.target.value)} /></Form.Item>
          <Form.Item label="分类"><Select allowClear style={{ width: 150 }} value={category} onChange={setCategory} options={categories.map((value) => ({ value, label: value }))} /></Form.Item>
        </Form>
      </div>
      <Row gutter={[12, 12]}>
        {data.map((product) => <Col key={product.id} xs={24} sm={12} lg={8} xl={6}><ProductCard product={product} /></Col>)}
      </Row>
      {isFetching ? <Typography.Text>正在加载食材...</Typography.Text> : null}
      <div className="sticky-mobile-action"><Button type="primary" block size="large" onClick={() => navigate('/unit/cart')}>查看采购清单</Button></div>
    </div>
  );
}
