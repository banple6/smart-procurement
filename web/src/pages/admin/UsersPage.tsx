import { CopyOutlined, PlusOutlined } from '@ant-design/icons';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App as AntApp, Button, Drawer, Form, Input, Modal, Select, Space, Switch, Table } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { usersApi, type AdminUser } from '@/api/users';
import { unitsApi } from '@/api/units';
import { queryClient } from '@/app/queryClient';
import { PageHeader } from '@/components/Page';
import { roleText } from '@/utils/status';
import { useState } from 'react';

const schema = z.object({
  username: z.string().min(3, '账号至少 3 位'),
  display_name: z.string().min(1, '请输入显示名称'),
  unit_id: z.string().min(1, '请选择所属单位'),
  password: z.string().min(8, '初始密码至少 8 位'),
});

type UserForm = z.infer<typeof schema>;

function randomPassword() {
  return `Jr${Math.random().toString(36).slice(2, 8)}${Math.floor(Math.random() * 90 + 10)}`;
}

export function UsersPage() {
  const { message, modal } = AntApp.useApp();
  const [open, setOpen] = useState(false);
  const [created, setCreated] = useState<{ username: string; password: string } | null>(null);
  const { data = [], isFetching } = useQuery({ queryKey: ['admin', 'users'], queryFn: usersApi.list });
  const { data: units = [] } = useQuery({ queryKey: ['admin', 'units'], queryFn: unitsApi.list });
  const { control, handleSubmit, reset, formState, setValue } = useForm<UserForm>({
    resolver: zodResolver(schema),
    defaultValues: { username: '', display_name: '', unit_id: '', password: randomPassword() },
  });
  const createMutation = useMutation({
    mutationFn: usersApi.create,
    onSuccess: async (user) => {
      setCreated({ username: user.username, password: user.initial_password });
      reset({ username: '', display_name: '', unit_id: '', password: randomPassword() });
      setOpen(false);
      await queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '创建失败'),
  });

  const resetPassword = (user: AdminUser) => {
    const password = randomPassword();
    modal.confirm({
      title: `重置密码：${user.username}`,
      content: '初始密码只会展示一次，请复制后交给使用人。',
      okText: '重置密码',
      cancelText: '取消',
      onOk: async () => {
        const result = await usersApi.resetPassword(user.id, { new_password: password, must_change_password: true });
        setCreated({ username: user.username, password: result.initial_password });
      },
    });
  };

  const setStatus = (user: AdminUser, active: boolean) => {
    modal.confirm({
      title: active ? '确认启用该账号吗？' : '确认停用该账号吗？',
      okText: active ? '启用' : '停用',
      cancelText: '取消',
      onOk: async () => {
        await usersApi.setStatus(user.id, active);
        message.success(active ? '账号已启用' : '账号已停用');
        await queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      },
    });
  };

  return (
    <div className="page-shell">
      <PageHeader title="账号管理" description="创建子单位账号、重置密码和强制退出设备" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>创建账号</Button>} />
      <Table
        rowKey="id"
        loading={isFetching}
        dataSource={data}
        columns={[
          { title: '登录账号', dataIndex: 'username' },
          { title: '显示名称', dataIndex: 'display_name' },
          { title: '所属单位', dataIndex: 'unit_name' },
          { title: '角色', dataIndex: 'role', render: roleText },
          { title: '最近登录', dataIndex: 'last_login_at', render: (value) => value || '暂无记录' },
          { title: '启用', render: (_, user) => <Switch checked={Boolean(user.active)} onChange={(checked) => setStatus(user, checked)} /> },
          { title: '操作', render: (_, user) => <Space wrap><Button onClick={() => resetPassword(user)}>重置密码</Button><Button onClick={() => usersApi.revokeSessions(user.id).then((r) => message.success(r.message))}>强制退出</Button></Space> },
        ]}
      />
      <Drawer title="创建子单位账号" open={open} onClose={() => setOpen(false)} destroyOnClose width={420}>
        <Form layout="vertical" onFinish={handleSubmit((values) => createMutation.mutate(values))}>
          <Form.Item label="登录账号" validateStatus={formState.errors.username ? 'error' : undefined} help={formState.errors.username?.message}>
            <Controller name="username" control={control} render={({ field }) => <Input {...field} />} />
          </Form.Item>
          <Form.Item label="显示名称" validateStatus={formState.errors.display_name ? 'error' : undefined} help={formState.errors.display_name?.message}>
            <Controller name="display_name" control={control} render={({ field }) => <Input {...field} />} />
          </Form.Item>
          <Form.Item label="所属单位" validateStatus={formState.errors.unit_id ? 'error' : undefined} help={formState.errors.unit_id?.message}>
            <Controller name="unit_id" control={control} render={({ field }) => <Select {...field} showSearch optionFilterProp="label" options={units.map((unit) => ({ value: unit.id, label: unit.unit_name }))} />} />
          </Form.Item>
          <Form.Item label="初始密码" validateStatus={formState.errors.password ? 'error' : undefined} help={formState.errors.password?.message}>
            <Controller name="password" control={control} render={({ field }) => <Input {...field} addonAfter={<Button type="link" onClick={() => setValue('password', randomPassword())}>生成</Button>} />} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={createMutation.isPending} block>创建账号</Button>
        </Form>
      </Drawer>
      <Modal title="初始密码只显示一次" open={Boolean(created)} onCancel={() => setCreated(null)} footer={<Button type="primary" onClick={() => setCreated(null)}>我已保存</Button>}>
        <p>账号：{created?.username}</p>
        <p>密码：{created?.password}</p>
        <Button icon={<CopyOutlined />} onClick={() => navigator.clipboard.writeText(`账号：${created?.username}\n密码：${created?.password}`)}>复制账号信息</Button>
      </Modal>
    </div>
  );
}
