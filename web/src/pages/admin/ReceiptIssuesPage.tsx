import { useMutation, useQuery } from '@tanstack/react-query';
import { App as AntApp, Button, Card, Input, Space, Table } from 'antd';
import { receiptIssuesApi } from '@/api/receiptIssues';
import { queryClient } from '@/app/queryClient';
import { PageHeader, StatusTag } from '@/components/Page';
import type { ReceiptIssue } from '@/types/domain';

export function ReceiptIssuesPage() {
  const { message, modal } = AntApp.useApp();
  const { data = [], isFetching } = useQuery({ queryKey: ['admin', 'receipt-issues'], queryFn: () => receiptIssuesApi.list() });
  const mutation = useMutation({
    mutationFn: ({ issue, note }: { issue: ReceiptIssue; note: string }) => receiptIssuesApi.resolve(issue.id, note),
    onSuccess: async () => {
      message.success('收货异常已处理');
      await queryClient.invalidateQueries({ queryKey: ['admin', 'receipt-issues'] });
      await queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
    },
  });
  const resolve = (issue: ReceiptIssue) => {
    let note = '';
    modal.confirm({
      title: '填写处理说明',
      content: <Input.TextArea rows={4} onChange={(e) => (note = e.target.value)} />,
      okText: '标记已处理',
      cancelText: '取消',
      onOk: () => mutation.mutateAsync({ issue, note }),
    });
  };
  return (
    <div className="page-shell">
      <PageHeader title="收货异常" description="查看并处理子单位提交的收货问题" />
      <Table
        rowKey="id"
        loading={isFetching}
        dataSource={data}
        expandable={{
          expandedRowRender: (issue) => (
            <Card>
              <p>{issue.description}</p>
              <Space wrap>{issue.photos?.map((photo) => <img key={photo.id} src={photo.thumbnail_url || photo.url} alt="异常照片" style={{ width: 96, height: 96, objectFit: 'cover' }} />)}</Space>
            </Card>
          ),
        }}
        columns={[
          { title: '订单编号', dataIndex: 'order_no' },
          { title: '单位', dataIndex: 'unit_name' },
          { title: '问题类型', dataIndex: 'issue_type' },
          { title: '状态', dataIndex: 'status', render: (status) => <StatusTag status={status} /> },
          { title: '提交时间', dataIndex: 'reported_at' },
          { title: '操作', render: (_, issue) => issue.status === 'open' ? <Button onClick={() => resolve(issue)}>处理</Button> : '已处理' },
        ]}
      />
    </div>
  );
}
