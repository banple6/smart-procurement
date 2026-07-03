import { LogoutOutlined, SaveOutlined } from '@ant-design/icons';
import { zodResolver } from '@hookform/resolvers/zod';
import { App as AntApp, Button, Card, Descriptions, Form, Input, Modal, Space } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { authApi } from '@/api/auth';
import { PageHeader } from '@/components/Page';
import { useAuth } from '@/auth/AuthContext';
import { queryClient } from '@/app/queryClient';

const passwordSchema = z
  .object({
    old_password: z.string().min(1, '请输入原密码'),
    new_password: z.string().min(8, '至少 8 位').regex(/[A-Za-z]/, '需包含字母').regex(/\d/, '需包含数字'),
    confirm: z.string().min(1, '请再次输入新密码'),
  })
  .refine((data) => data.new_password === data.confirm, { path: ['confirm'], message: '两次输入的新密码不一致' });

type PasswordForm = z.infer<typeof passwordSchema>;

export function AccountPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { message, modal } = AntApp.useApp();
  const { control, handleSubmit, formState, reset } = useForm<PasswordForm>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { old_password: '', new_password: '', confirm: '' },
  });

  const onPasswordSubmit = handleSubmit(async (values) => {
    try {
      await authApi.changePassword({ old_password: values.old_password, new_password: values.new_password });
      reset();
      await queryClient.clear();
      Modal.info({
        title: '密码已修改',
        content: '请使用新密码重新登录。',
        okText: '重新登录',
        onOk: async () => {
          await logout();
          navigate('/login', { replace: true });
        },
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '修改失败，请稍后重试');
    }
  });

  return (
    <div className="page-shell">
      <PageHeader title="账号信息" description="查看当前账号和修改密码" />
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card>
          <Descriptions column={{ xs: 1, md: 2 }} bordered size="middle">
            <Descriptions.Item label="账号">{user?.username}</Descriptions.Item>
            <Descriptions.Item label="显示名称">{user?.display_name}</Descriptions.Item>
            <Descriptions.Item label="所属单位">{user?.role === 'admin' ? '系统管理员' : user?.unit_name}</Descriptions.Item>
            <Descriptions.Item label="默认配送点">{user?.role === 'admin' ? '无' : user?.default_delivery_point}</Descriptions.Item>
            <Descriptions.Item label="最近登录">{user?.last_login_at || '暂无记录'}</Descriptions.Item>
          </Descriptions>
        </Card>
        <Card title="修改密码">
          <Form layout="vertical" onFinish={onPasswordSubmit} style={{ maxWidth: 520 }}>
            <Form.Item label="原密码" validateStatus={formState.errors.old_password ? 'error' : undefined} help={formState.errors.old_password?.message}>
              <Controller name="old_password" control={control} render={({ field }) => <Input.Password {...field} autoComplete="current-password" />} />
            </Form.Item>
            <Form.Item label="新密码" validateStatus={formState.errors.new_password ? 'error' : undefined} help={formState.errors.new_password?.message}>
              <Controller name="new_password" control={control} render={({ field }) => <Input.Password {...field} autoComplete="new-password" />} />
            </Form.Item>
            <Form.Item label="确认新密码" validateStatus={formState.errors.confirm ? 'error' : undefined} help={formState.errors.confirm?.message}>
              <Controller name="confirm" control={control} render={({ field }) => <Input.Password {...field} autoComplete="new-password" />} />
            </Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={formState.isSubmitting} icon={<SaveOutlined />}>保存新密码</Button>
              <Button
                danger
                icon={<LogoutOutlined />}
                onClick={() => modal.confirm({ title: '确认退出登录吗？', okText: '退出登录', cancelText: '取消', onOk: logout })}
              >
                退出登录
              </Button>
            </Space>
          </Form>
        </Card>
      </Space>
    </div>
  );
}
