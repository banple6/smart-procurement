import { EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, App as AntApp, Button, Card, Drawer, Form, Input, InputNumber, Select, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { productsApi } from '@/api/products';
import { queryClient } from '@/app/queryClient';
import { EmptyState, MobileCardList, PageHeader, StatusTag } from '@/components/Page';
import type { Product } from '@/types/domain';
import { availableQuantity } from '@/utils/inventory';
import { formatMoney, yuanToCents } from '@/utils/money';
import { productFormToPayload, ProductForm, type ProductFormValues } from './ProductForm';

export function ProductsPage() {
  const { message, modal } = AntApp.useApp();
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get('q') || '');
  const [category, setCategory] = useState<string | undefined>();
  const statusFilter = params.get('status') || undefined;
  const [editing, setEditing] = useState<Product | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(params.get('create') === '1');
  const [pendingImage, setPendingImage] = useState<File | null>(null);
  const { data = [], isFetching, refetch } = useQuery({ queryKey: ['products', q, category], queryFn: () => productsApi.list({ q, category }) });

  useEffect(() => {
    if (params.get('create') === '1') setDrawerOpen(true);
  }, [params]);

  const filteredData = useMemo(() => {
    if (statusFilter !== 'tight') return data;
    return data.filter((item) => Number(item.available_quantity ?? Number(item.stock_quantity) - Number(item.reserved_quantity)) <= Number(item.warning_quantity) || item.supply_status === 'tight');
  }, [data, statusFilter]);
  const categories = useMemo(() => Array.from(new Set(data.map((item) => item.category))).filter(Boolean), [data]);

  const saveMutation = useMutation({
    mutationFn: async (values: ProductFormValues) => {
      const payload = productFormToPayload(values);
      const product = editing ? await productsApi.update(editing.id, payload) : await productsApi.create(payload);
      if (pendingImage) await productsApi.uploadImage(product.id, pendingImage);
      return product;
    },
    onSuccess: async () => {
      message.success('保存成功');
      setDrawerOpen(false);
      setEditing(null);
      setPendingImage(null);
      params.delete('create');
      setParams(params);
      await queryClient.invalidateQueries({ queryKey: ['products'] });
      await queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '保存失败'),
  });

  const quickPrice = (product: Product) => {
    let price = (product.price_cents / 100).toFixed(2);
    let reason = '';
    modal.confirm({
      title: `修改价格：${product.name}`,
      content: (
        <Space direction="vertical" style={{ width: '100%' }}>
          <InputNumber min={0.01} precision={2} defaultValue={Number(price)} addonBefore="¥" style={{ width: '100%' }} onChange={(value) => (price = String(value || ''))} />
          <Input placeholder="改价原因" onChange={(event) => (reason = event.target.value)} />
        </Space>
      ),
      okText: '保存价格',
      cancelText: '取消',
      onOk: async () => {
        await productsApi.changePrice(product.id, { price_cents: yuanToCents(price), reason, expected_updated_at: product.updated_at });
        message.success('价格已更新');
        await queryClient.invalidateQueries({ queryKey: ['products'] });
        await queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      },
    });
  };

  const adjustStock = (product: Product) => {
    let mode: 'set' | 'increase' | 'decrease' = 'set';
    let quantity = product.stock_quantity;
    let reason = '';
    modal.confirm({
      title: `调整库存：${product.name}`,
      content: (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            defaultValue="set"
            options={[
              { value: 'set', label: '调整后总库存' },
              { value: 'increase', label: '本次入库数量' },
              { value: 'decrease', label: '本次减少数量' },
            ]}
            onChange={(value: 'set' | 'increase' | 'decrease') => (mode = value)}
          />
          <Input defaultValue={quantity} onChange={(event) => (quantity = event.target.value)} />
          <Input placeholder="库存调整原因" onChange={(event) => (reason = event.target.value)} />
        </Space>
      ),
      okText: '保存库存',
      cancelText: '取消',
      onOk: async () => {
        await productsApi.adjustInventory(product.id, { mode, quantity, reason, expected_updated_at: product.updated_at });
        message.success('库存调整成功');
        await queryClient.invalidateQueries({ queryKey: ['products'] });
        await queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      },
    });
  };

  const patchStatus = async (product: Product, supply_status: string, active = true) => {
    await productsApi.patchStatus(product.id, { supply_status, active });
    message.success('状态已更新');
    await queryClient.invalidateQueries({ queryKey: ['products'] });
  };

  const columns: ColumnsType<Product> = [
    {
      title: '图片',
      dataIndex: 'image_path',
      width: 88,
      render: (src) => (src ? <img src={src} alt="" style={{ width: 54, height: 54, objectFit: 'cover', borderRadius: 6 }} /> : <div style={{ width: 54, height: 54, background: '#F3F6FB', display: 'grid', placeItems: 'center', borderRadius: 6 }}>暂无图片</div>),
    },
    { title: '商品名称', dataIndex: 'name' },
    { title: '分类', dataIndex: 'category' },
    { title: '规格', dataIndex: 'spec' },
    { title: '单位', dataIndex: 'unit' },
    { title: '价格', dataIndex: 'price_cents', render: formatMoney },
    { title: '总库存', dataIndex: 'stock_quantity' },
    { title: '预占', dataIndex: 'reserved_quantity' },
    { title: '可用', render: (_, record) => `${availableQuantity(record)} ${record.unit}` },
    { title: '状态', dataIndex: 'supply_status', render: (status) => <StatusTag status={status} /> },
    {
      title: '操作',
      fixed: 'right',
      width: 260,
      render: (_, record) => (
        <Space wrap>
          <Button onClick={() => quickPrice(record)}>改价格</Button>
          <Button onClick={() => adjustStock(record)}>调库存</Button>
          <Button icon={<EditOutlined />} onClick={() => { setEditing(record); setDrawerOpen(true); }}>编辑</Button>
          <Select
            size="small"
            value="more"
            style={{ width: 92 }}
            onChange={(value) => {
              if (value === 'pause') void patchStatus(record, 'paused', true);
              if (value === 'off') void patchStatus(record, 'off_shelf', false);
              if (value === 'normal') void patchStatus(record, 'normal', true);
              if (value === 'delete') {
                modal.confirm({ title: '确认删除该食材吗？', okText: '删除', okButtonProps: { danger: true }, onOk: async () => { await productsApi.remove(record.id); await queryClient.invalidateQueries({ queryKey: ['products'] }); } });
              }
              if (value === 'restore') void productsApi.restore(record.id).then(() => queryClient.invalidateQueries({ queryKey: ['products'] }));
            }}
            options={[
              { value: 'more', label: '更多' },
              { value: 'normal', label: '正常供应' },
              { value: 'pause', label: '暂停供应' },
              { value: 'off', label: '下架' },
              { value: 'delete', label: '软删除' },
              { value: 'restore', label: '恢复' },
            ]}
          />
        </Space>
      ),
    },
  ];

  return (
    <div className="page-shell">
      <PageHeader
        title="食材管理"
        description="管理食材资料、价格、库存和供应状态"
        extra={[
          <Button key="refresh" icon={<ReloadOutlined />} onClick={() => refetch()}>刷新</Button>,
          <Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); setDrawerOpen(true); }}>新增食材</Button>,
        ]}
      />
      <div className="filter-panel">
        <Form layout="inline">
          <Form.Item label="搜索">
            <Input allowClear placeholder="食材名称" value={q} onChange={(e) => setQ(e.target.value)} />
          </Form.Item>
          <Form.Item label="分类">
            <Select allowClear style={{ width: 150 }} value={category} onChange={setCategory} options={categories.map((value) => ({ value, label: value }))} />
          </Form.Item>
        </Form>
      </div>
      {statusFilter === 'tight' ? <Alert type="warning" showIcon message="当前仅显示库存预警食材" style={{ marginBottom: 12 }} /> : null}
      <Table className="desktop-table" rowKey="id" loading={isFetching} dataSource={filteredData} columns={columns} scroll={{ x: 1280 }} pagination={{ pageSize: 20 }} />
      <MobileCardList
        items={filteredData}
        render={(item) => (
          <Card key={item.id}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <strong>{item.name}</strong>
              <span>{item.spec} · {formatMoney(item.price_cents)} / {item.unit}</span>
              <span>总库存 {item.stock_quantity}，预占 {item.reserved_quantity}，可用 {availableQuantity(item)}</span>
              <StatusTag status={item.supply_status} />
              <Space>
                <Button onClick={() => quickPrice(item)}>改价格</Button>
                <Button onClick={() => adjustStock(item)}>调库存</Button>
                <Button onClick={() => { setEditing(item); setDrawerOpen(true); }}>编辑</Button>
              </Space>
            </Space>
          </Card>
        )}
      />
      {!filteredData.length && !isFetching ? <EmptyState /> : null}
      <Drawer width={460} title={editing ? '编辑食材' : '新增食材'} open={drawerOpen} onClose={() => { setDrawerOpen(false); setEditing(null); }} destroyOnClose>
        <ProductForm product={editing} loading={saveMutation.isPending} onImageChange={setPendingImage} onSubmit={(values) => saveMutation.mutate(values)} />
      </Drawer>
    </div>
  );
}
