import { PlusOutlined } from '@ant-design/icons';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App as AntApp, Button, Drawer, Form, Input, Space, Switch, Table } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { unitsApi } from '@/api/units';
import { queryClient } from '@/app/queryClient';
import { PageHeader } from '@/components/Page';
import type { Unit } from '@/types/domain';
import { useState } from 'react';

const unitSchema = z.object({
  unit_code: z.string().min(1, '请输入单位编码'),
  unit_name: z.string().min(1, '请输入单位名称'),
  default_delivery_point: z.string().min(1, '请输入默认配送点'),
});

type UnitForm = z.infer<typeof unitSchema>;

function UnitEditor({ unit, onClose }: { unit: Unit | null; onClose: () => void }) {
  const { message } = AntApp.useApp();
  const { control, handleSubmit, formState } = useForm<UnitForm>({
    resolver: zodResolver(unitSchema),
    defaultValues: {
      unit_code: unit?.unit_code || '',
      unit_name: unit?.unit_name || '',
      default_delivery_point: unit?.default_delivery_point || '',
    },
  });
  const mutation = useMutation({
    mutationFn: (values: UnitForm) => (unit ? unitsApi.update(unit.id, values) : unitsApi.create(values)),
    onSuccess: async () => {
      message.success('保存成功');
      await queryClient.invalidateQueries({ queryKey: ['admin', 'units'] });
      onClose();
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '保存失败'),
  });
  return (
    <Form layout="vertical" onFinish={handleSubmit((values) => mutation.mutate(values))}>
      <Form.Item label="单位编码" validateStatus={formState.errors.unit_code ? 'error' : undefined} help={formState.errors.unit_code?.message}>
        <Controller name="unit_code" control={control} render={({ field }) => <Input {...field} />} />
      </Form.Item>
      <Form.Item label="单位名称" validateStatus={formState.errors.unit_name ? 'error' : undefined} help={formState.errors.unit_name?.message}>
        <Controller name="unit_name" control={control} render={({ field }) => <Input {...field} />} />
      </Form.Item>
      <Form.Item label="默认配送点" validateStatus={formState.errors.default_delivery_point ? 'error' : undefined} help={formState.errors.default_delivery_point?.message}>
        <Controller name="default_delivery_point" control={control} render={({ field }) => <Input {...field} />} />
      </Form.Item>
      <Button type="primary" htmlType="submit" block loading={mutation.isPending}>保存</Button>
    </Form>
  );
}

export function UnitsPage() {
  const { message, modal } = AntApp.useApp();
  const [editing, setEditing] = useState<Unit | null>(null);
  const [open, setOpen] = useState(false);
  const { data = [], isFetching } = useQuery({ queryKey: ['admin', 'units'], queryFn: unitsApi.list });

  const setStatus = (unit: Unit, active: boolean) => {
    modal.confirm({
      title: active ? '确认启用该单位吗？' : '确认停用该单位吗？',
      okText: active ? '启用' : '停用',
      cancelText: '取消',
      onOk: async () => {
        await unitsApi.setStatus(unit.id, active);
        message.success(active ? '单位已启用' : '单位已停用');
        await queryClient.invalidateQueries({ queryKey: ['admin', 'units'] });
      },
    });
  };

  return (
    <div className="page-shell">
      <PageHeader title="子单位管理" description="新增、编辑、启用和停用内部子单位" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); setOpen(true); }}>新增单位</Button>} />
      <Table
        rowKey="id"
        loading={isFetching}
        dataSource={data}
        columns={[
          { title: '单位名称', dataIndex: 'unit_name' },
          { title: '单位编码', dataIndex: 'unit_code' },
          { title: '默认配送点', dataIndex: 'default_delivery_point' },
          { title: '启用状态', render: (_, unit) => <Switch checked={Boolean(unit.active)} onChange={(checked) => setStatus(unit, checked)} /> },
          { title: '操作', render: (_, unit) => <Space><Button onClick={() => { setEditing(unit); setOpen(true); }}>编辑</Button><Button onClick={() => location.assign(`/admin/orders?unit_id=${unit.id}`)}>查看订单</Button></Space> },
        ]}
      />
      <Drawer title={editing ? '编辑单位' : '新增单位'} open={open} onClose={() => setOpen(false)} destroyOnClose width={420}>
        <UnitEditor unit={editing} onClose={() => setOpen(false)} />
      </Drawer>
    </div>
  );
}
