import { zodResolver } from '@hookform/resolvers/zod';
import { Form, Input, InputNumber, Select, Upload, Button } from 'antd';
import type { UploadFile } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import type { Product } from '@/types/domain';
import type { SupplyStatus } from '@/types/domain';

const schema = z.object({
  name: z.string().min(1, '请输入名称'),
  category: z.string().min(1, '请选择分类'),
  spec: z.string().min(1, '请输入规格'),
  unit: z.string().min(1, '请输入单位'),
  price_yuan: z.number().min(0.01, '请输入单价'),
  stock_quantity: z.string().min(1, '请输入库存'),
  min_order_quantity: z.string().default('1'),
  quantity_step: z.string().default('1'),
  warning_quantity: z.string().default('0'),
  supply_status: z.string().default('normal'),
  active: z.boolean().default(true),
});

export type ProductFormValues = z.infer<typeof schema>;

export function productFormToPayload(values: ProductFormValues) {
  return {
    name: values.name,
    category: values.category,
    spec: values.spec,
    unit: values.unit,
    price_cents: Math.round(values.price_yuan * 100),
    stock_quantity: values.stock_quantity,
    min_order_quantity: values.min_order_quantity,
    quantity_step: values.quantity_step,
    warning_quantity: values.warning_quantity,
    supply_status: values.supply_status as SupplyStatus,
    active: values.active,
  };
}

export function ProductForm({ product, loading, onSubmit, onImageChange }: { product?: Product | null; loading?: boolean; onSubmit: (values: ProductFormValues) => void; onImageChange?: (file: File) => void }) {
  const { control, handleSubmit, formState } = useForm<ProductFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: product?.name || '',
      category: product?.category || '蔬菜',
      spec: product?.spec || '',
      unit: product?.unit || '公斤',
      price_yuan: (product?.price_cents || 0) / 100,
      stock_quantity: product?.stock_quantity || '0',
      min_order_quantity: product?.min_order_quantity || '1',
      quantity_step: product?.quantity_step || '1',
      warning_quantity: product?.warning_quantity || '0',
      supply_status: product?.supply_status || 'normal',
      active: Boolean(product?.active ?? true),
    },
  });

  return (
    <Form layout="vertical" onFinish={handleSubmit(onSubmit)} requiredMark>
      <Form.Item label="图片">
        <Upload
          accept="image/*"
          maxCount={1}
          beforeUpload={(file) => {
            onImageChange?.(file);
            return false;
          }}
          defaultFileList={product?.image_path ? ([{ uid: product.id, name: '当前图片', status: 'done', url: product.image_path }] as UploadFile[]) : []}
        >
          <Button>选择图片</Button>
        </Upload>
      </Form.Item>
      <Form.Item label="名称" validateStatus={formState.errors.name ? 'error' : undefined} help={formState.errors.name?.message}>
        <Controller name="name" control={control} render={({ field }) => <Input {...field} />} />
      </Form.Item>
      <Form.Item label="分类">
        <Controller name="category" control={control} render={({ field }) => <Select {...field} options={['蔬菜', '水果', '肉禽', '水产', '粮油', '其他'].map((v) => ({ value: v, label: v }))} />} />
      </Form.Item>
      <Form.Item label="规格">
        <Controller name="spec" control={control} render={({ field }) => <Input {...field} />} />
      </Form.Item>
      <Form.Item label="单位">
        <Controller name="unit" control={control} render={({ field }) => <Input {...field} />} />
      </Form.Item>
      <Form.Item label="单价">
        <Controller name="price_yuan" control={control} render={({ field }) => <InputNumber {...field} min={0} precision={2} addonBefore="¥" style={{ width: '100%' }} />} />
      </Form.Item>
      <Form.Item label="库存">
        <Controller name="stock_quantity" control={control} render={({ field }) => <Input {...field} />} />
      </Form.Item>
      <Form.Item label="更多信息">
        <Controller name="min_order_quantity" control={control} render={({ field }) => <Input {...field} addonBefore="最小申领量" />} />
      </Form.Item>
      <Form.Item>
        <Controller name="quantity_step" control={control} render={({ field }) => <Input {...field} addonBefore="步长" />} />
      </Form.Item>
      <Form.Item>
        <Controller name="warning_quantity" control={control} render={({ field }) => <Input {...field} addonBefore="预警库存" />} />
      </Form.Item>
      <Form.Item>
        <Controller
          name="supply_status"
          control={control}
          render={({ field }) => (
            <Select
              {...field}
              options={[
                { value: 'normal', label: '正常供应' },
                { value: 'tight', label: '库存紧张' },
                { value: 'paused', label: '暂停供应' },
                { value: 'off_shelf', label: '已下架' },
              ]}
            />
          )}
        />
      </Form.Item>
      <Button type="primary" htmlType="submit" loading={loading} block>保存</Button>
    </Form>
  );
}
