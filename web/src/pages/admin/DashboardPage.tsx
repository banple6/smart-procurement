import {
  AlertOutlined,
  ArrowRightOutlined,
  AuditOutlined,
  DatabaseOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  ReloadOutlined,
  ShopOutlined,
  TruckOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, App as AntApp, Button, Card, Empty, Progress, Segmented, Skeleton, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { dashboardApi } from '@/api/dashboard';
import { ordersApi } from '@/api/orders';
import { queryClient } from '@/app/queryClient';
import { StatusTag } from '@/components/Page';
import type { DashboardMetricKey, DashboardOverview, Order } from '@/types/domain';
import { dashboardMetricDefinitions, describeSeconds, formatPercent, taskTargetForType } from '@/utils/dashboard';
import { formatMoney } from '@/utils/money';
import { nextAdminOrderAction } from '@/utils/status';

function formatClock(value?: string) {
  if (!value) return '暂无记录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date);
}

function formatShortDate(value: string) {
  const date = new Date(`${value}T00:00:00+08:00`);
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit' }).format(date);
}

function metricValue(key: DashboardMetricKey, overview: DashboardOverview) {
  if (key === 'today_total_cents') return formatMoney(overview.metrics[key]);
  return overview.metrics[key];
}

function metricHint(key: DashboardMetricKey, overview: DashboardOverview) {
  if (key === 'today_valid_orders') return `较昨日 ${formatPercent(overview.comparisons.orders_vs_yesterday_percent)}`;
  if (key === 'today_total_cents') return `较昨日 ${formatPercent(overview.comparisons.amount_vs_yesterday_percent)}`;
  const task = overview.tasks.find((item) => itemTargetMatchesMetric(item.type, key));
  if (task?.oldest_wait_seconds) return `最早等待 ${describeSeconds(task.oldest_wait_seconds)}`;
  return dashboardMetricDefinitions.find((item) => item.key === key)?.description || '';
}

function itemTargetMatchesMetric(type: string, key: DashboardMetricKey) {
  return (type === 'pending_orders' && key === 'pending') || (type === 'waiting_shipment' && key === 'waiting_shipment') || (type === 'receipt_issues' && key === 'open_receipt_issues') || (type === 'tight_inventory' && key === 'tight_inventory');
}

function DashboardSkeleton() {
  return (
    <div className="dashboard-workbench">
      <Skeleton active paragraph={{ rows: 2 }} />
      <div className="dashboard-metrics-grid">
        {Array.from({ length: 8 }).map((_, index) => <Skeleton.Node key={index} active className="metric-skeleton" />)}
      </div>
      <Skeleton active paragraph={{ rows: 8 }} />
    </div>
  );
}

function MetricGrid({ overview }: { overview: DashboardOverview }) {
  const navigate = useNavigate();
  return (
    <div className="dashboard-metrics-grid">
      {dashboardMetricDefinitions.map((definition) => (
        <button
          key={definition.key}
          type="button"
          className={`dashboard-metric-card tone-${definition.tone}`}
          onClick={() => navigate(definition.target)}
        >
          <span>{definition.title}</span>
          <strong>{metricValue(definition.key, overview)}</strong>
          <em>{metricHint(definition.key, overview)}</em>
          <small>{definition.description}</small>
        </button>
      ))}
    </div>
  );
}

function TaskCenter({ overview }: { overview: DashboardOverview }) {
  const navigate = useNavigate();
  const urgent = overview.tasks.filter((task) => task.count > 0 && task.priority !== 'normal');
  return (
    <Card className="dashboard-card dashboard-task-card" title="待办中心" extra={<Button type="link" onClick={() => navigate('/admin/orders')}>全部订单</Button>}>
      {urgent.length ? null : <Alert showIcon type="success" message="暂无紧急待办" style={{ marginBottom: 12 }} />}
      <div className="dashboard-task-list">
        {overview.tasks.map((task) => (
          <div className={`dashboard-task-item priority-${task.priority}`} key={task.type}>
            <div>
              <Typography.Text strong>{task.title}</Typography.Text>
              <Typography.Text type="secondary">
                {task.oldest_wait_seconds ? `最早一笔已等待 ${describeSeconds(task.oldest_wait_seconds)}` : task.description}
              </Typography.Text>
            </div>
            <strong>{task.count ? `${task.count} 项` : '0 项'}</strong>
            <Button onClick={() => navigate(task.target_url || taskTargetForType(task.type))}>{task.action_label}</Button>
          </div>
        ))}
      </div>
    </Card>
  );
}

function TrendChart({
  overview,
  rangeDays,
  onRangeChange,
}: {
  overview: DashboardOverview;
  rangeDays: 7 | 14 | 30;
  onRangeChange: (value: 7 | 14 | 30) => void;
}) {
  const navigate = useNavigate();
  const maxOrders = Math.max(1, ...overview.trend.map((item) => item.order_count));
  const maxAmount = Math.max(1, ...overview.trend.map((item) => item.amount_cents));
  const points = overview.trend.map((item, index) => {
    const x = overview.trend.length === 1 ? 50 : (index / (overview.trend.length - 1)) * 100;
    const y = 92 - (item.amount_cents / maxAmount) * 78;
    return `${x},${y}`;
  }).join(' ');
  const totalOrders = overview.trend.reduce((sum, item) => sum + item.order_count, 0);
  const totalAmount = overview.trend.reduce((sum, item) => sum + item.amount_cents, 0);

  return (
    <Card
      className="dashboard-card"
      title={rangeDays === 7 ? '近 7 日采购趋势' : `近 ${rangeDays} 日采购趋势`}
      extra={<Segmented size="small" value={rangeDays} onChange={(value) => onRangeChange(value as 7 | 14 | 30)} options={[7, 14, 30].map((value) => ({ value, label: `近 ${value} 日` }))} />}
    >
      {overview.trend.length ? (
        <>
          <div className="trend-summary">
            <span>有效订单 {totalOrders} 笔</span>
            <span>采购金额 {formatMoney(totalAmount)}</span>
          </div>
          <div className="trend-chart" role="img" aria-label="采购趋势图">
            <svg viewBox="0 0 100 100" preserveAspectRatio="none">
              <polyline points={points} fill="none" stroke="#175AA6" strokeWidth="2.4" vectorEffect="non-scaling-stroke" />
            </svg>
            <div className="trend-bars">
              {overview.trend.map((item) => (
                <Tooltip key={item.date} title={`${item.date}：${item.order_count} 笔，${formatMoney(item.amount_cents)}`}>
                  <div className="trend-bar-wrap">
                    <div className="trend-bar" style={{ height: `${Math.max(4, (item.order_count / maxOrders) * 82)}%` }} />
                    <span>{formatShortDate(item.date)}</span>
                  </div>
                </Tooltip>
              ))}
            </div>
          </div>
          <Button type="link" onClick={() => navigate('/admin/ledger')}>查看采购台账 <ArrowRightOutlined /></Button>
        </>
      ) : <Empty description="暂无趋势数据" />}
    </Card>
  );
}

function RecentOrders({ overview }: { overview: DashboardOverview }) {
  const { message, modal } = AntApp.useApp();
  const navigate = useNavigate();
  const [workingOrderId, setWorkingOrderId] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: ({ order, status }: { order: Order; status: string }) => ordersApi.adminStatus(order.id, status),
    onSuccess: async () => {
      message.success('订单状态已更新');
      await queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '操作失败，请稍后重试'),
    onSettled: () => setWorkingOrderId(null),
  });

  const runAction = (order: Order) => {
    const action = nextAdminOrderAction(order.status);
    if (!action) {
      navigate(`/admin/orders?order_no=${order.order_no}`);
      return;
    }
    if (action.ship) {
      navigate('/admin/orders?status=preparing');
      return;
    }
    modal.confirm({
      title: `确认${action.label}吗？`,
      okText: action.label,
      cancelText: '取消',
      onOk: () => {
        setWorkingOrderId(order.id);
        mutation.mutate({ order, status: action.nextStatus! });
      },
    });
  };

  const columns: ColumnsType<DashboardOverview['recent_orders'][number]> = [
    { title: '订单编号', dataIndex: 'order_no', width: 160, render: (value) => <Button type="link" onClick={() => navigate(`/admin/orders?order_no=${value}`)}>{value}</Button> },
    { title: '子单位', dataIndex: 'unit_name_snapshot', width: 150 },
    { title: '下单时间', dataIndex: 'created_at', width: 150 },
    { title: '商品种类', dataIndex: 'item_count', width: 90, render: (value) => `${value || 0} 种` },
    { title: '订单金额', dataIndex: 'total_cents', width: 110, render: formatMoney },
    { title: '当前状态', dataIndex: 'status', width: 110, render: (status) => <StatusTag status={status} /> },
    { title: '异常标记', dataIndex: 'open_receipt_issue_count', width: 100, render: (value) => (value ? <Tag color="error">有异常</Tag> : '—') },
    {
      title: '操作',
      width: 120,
      render: (_, order) => {
        const action = nextAdminOrderAction(order.status);
        return <Button loading={workingOrderId === order.id} onClick={() => runAction(order)}>{action?.ship ? '查看发货' : action?.label || '查看'}</Button>;
      },
    },
  ];

  return (
    <Card className="dashboard-card" title="最近订单" extra={<Button type="link" onClick={() => navigate('/admin/orders')}>查看全部订单</Button>}>
      <Table rowKey="id" size="middle" pagination={false} dataSource={overview.recent_orders} columns={columns} scroll={{ x: 980 }} locale={{ emptyText: '暂无订单' }} />
    </Card>
  );
}

function InventoryAlerts({ overview }: { overview: DashboardOverview }) {
  const navigate = useNavigate();
  const columns: ColumnsType<DashboardOverview['inventory_alerts'][number]> = [
    { title: '食材名称', dataIndex: 'name' },
    { title: '单位', dataIndex: 'unit', width: 70 },
    { title: '总库存', dataIndex: 'stock_quantity', width: 90 },
    { title: '预占库存', dataIndex: 'reserved_quantity', width: 90 },
    { title: '可用库存', dataIndex: 'available_quantity', width: 90 },
    { title: '预警值', dataIndex: 'warning_quantity', width: 80 },
    { title: '状态', dataIndex: 'supply_status', width: 110, render: (status) => <StatusTag status={status} /> },
    { title: '操作', width: 110, render: (_, item) => <Button onClick={() => navigate(`/admin/products?status=tight&q=${encodeURIComponent(item.name)}`)}>调整库存</Button> },
  ];
  return (
    <Card className="dashboard-card" title="库存预警" extra={<Button type="link" onClick={() => navigate('/admin/products?status=tight')}>查看库存</Button>}>
      <Table rowKey="id" size="middle" pagination={false} dataSource={overview.inventory_alerts} columns={columns} scroll={{ x: 760 }} locale={{ emptyText: '暂无库存预警' }} />
    </Card>
  );
}

function RankPanels({ overview }: { overview: DashboardOverview }) {
  const [unitSort, setUnitSort] = useState<'amount' | 'orders'>('amount');
  const navigate = useNavigate();
  const units = [...overview.unit_rank].sort((a, b) => unitSort === 'amount' ? b.amount_cents - a.amount_cents : b.order_count - a.order_count);
  return (
    <div className="dashboard-rank-grid">
      <Card className="dashboard-card" title="今日需求排行" extra={<Button type="link" onClick={() => navigate('/admin/preparation')}>今日备货汇总</Button>}>
        <Table
          rowKey="product_id"
          size="small"
          pagination={false}
          dataSource={overview.demand_rank}
          columns={[
            { title: '排名', width: 64, render: (_, __, index) => index + 1 },
            { title: '食材名称', dataIndex: 'name' },
            { title: '数量', dataIndex: 'quantity', render: (value, row) => `${value} ${row.unit}` },
            { title: '涉及单位', dataIndex: 'unit_count', render: (value) => `${value} 个` },
            { title: '订单数', dataIndex: 'order_count', render: (value) => `${value} 笔` },
          ]}
          locale={{ emptyText: '今日暂无需求数据' }}
        />
      </Card>
      <Card
        className="dashboard-card"
        title="今日单位采购情况"
        extra={<Segmented size="small" value={unitSort} onChange={(value) => setUnitSort(value as 'amount' | 'orders')} options={[{ value: 'amount', label: '按金额' }, { value: 'orders', label: '按订单量' }]} />}
      >
        <Table
          rowKey="unit_id"
          size="small"
          pagination={false}
          dataSource={units}
          columns={[
            { title: '单位名称', dataIndex: 'unit_name', render: (value, row) => <Button type="link" onClick={() => navigate(`/admin/orders?unit_id=${row.unit_id}&date=today`)}>{value}</Button> },
            { title: '订单数量', dataIndex: 'order_count', render: (value) => `${value} 笔` },
            { title: '采购金额', dataIndex: 'amount_cents', render: formatMoney },
            { title: '商品种类', dataIndex: 'product_count', render: (value) => `${value} 种` },
            { title: '异常数量', dataIndex: 'issue_count', render: (value) => (value ? <Tag color="error">{value} 个</Tag> : '—') },
          ]}
          locale={{ emptyText: '今日暂无单位采购数据' }}
        />
      </Card>
    </div>
  );
}

function QuickActions() {
  const navigate = useNavigate();
  const actions = [
    { label: '今日备货单', icon: <FileTextOutlined />, target: '/admin/preparation' },
    { label: '单位配送单', icon: <TruckOutlined />, target: '/admin/delivery' },
    { label: '待接单订单', icon: <AuditOutlined />, target: '/admin/orders?status=pending' },
    { label: '收货异常', icon: <AlertOutlined />, target: '/admin/receipt-issues?status=open' },
    { label: '食材价格维护', icon: <ShopOutlined />, target: '/admin/products?quick=price' },
    { label: '导出今日台账', icon: <FileExcelOutlined />, target: '/admin/ledger?export=today' },
  ];
  return (
    <Card className="dashboard-card" title="快捷操作">
      <div className="quick-action-grid">
        {actions.map((action) => (
          <button key={action.label} type="button" onClick={() => navigate(action.target)}>
            {action.icon}
            <span>{action.label}</span>
          </button>
        ))}
      </div>
    </Card>
  );
}

function SystemStatus({ overview }: { overview: DashboardOverview }) {
  const disk = Number(overview.system_status.disk_usage_percent || 0);
  return (
    <Card className="dashboard-card" title="系统状态">
      <div className="system-status-grid">
        <span>服务状态</span><strong>{overview.system_status.service === 'ok' ? '正常' : '异常'}</strong>
        <span>最近数据同步</span><strong>{formatClock(overview.system_status.refreshed_at)}</strong>
        <span>最近备份</span><strong>{overview.system_status.last_backup_at ? formatClock(overview.system_status.last_backup_at) : '暂无记录'}</strong>
        <span>磁盘使用</span><Progress percent={disk} size="small" status={disk >= 90 ? 'exception' : disk >= 80 ? 'active' : 'normal'} />
        <span>当前版本</span><strong>{overview.system_status.version}</strong>
      </div>
    </Card>
  );
}

export function DashboardPage() {
  const [rangeDays, setRangeDays] = useState<7 | 14 | 30>(7);
  const query = useQuery({
    queryKey: ['admin', 'dashboard', 'overview', rangeDays],
    queryFn: () => dashboardApi.overview({ range_days: rangeDays }),
    retry: false,
    refetchInterval: () => (document.hidden ? false : 60_000),
    placeholderData: (previous) => previous,
  });

  useEffect(() => {
    const refreshWhenVisible = () => {
      if (!document.hidden) void query.refetch();
    };
    document.addEventListener('visibilitychange', refreshWhenVisible);
    return () => document.removeEventListener('visibilitychange', refreshWhenVisible);
  }, [query]);

  if (query.isLoading && !query.data) {
    return <DashboardSkeleton />;
  }

  if (!query.data) {
    return (
      <div className="dashboard-workbench">
        <Alert type="error" showIcon message="工作台数据加载失败" description={query.error instanceof Error ? query.error.message : '请稍后重试'} action={<Button onClick={() => query.refetch()}>重新加载</Button>} />
      </div>
    );
  }

  const overview = query.data;

  return (
    <div className="dashboard-workbench">
      <div className="dashboard-toolbar">
        <div>
          <Typography.Title level={2}>桌面采购工作台</Typography.Title>
          <Typography.Text type="secondary">业务日期：{overview.business_date}，更新于 {formatClock(overview.refreshed_at)}</Typography.Text>
        </div>
        <Space>
          {query.isError ? <Tag color="warning">数据可能不是最新</Tag> : null}
          <Segmented value={rangeDays} onChange={(value) => setRangeDays(value as 7 | 14 | 30)} options={[7, 14, 30].map((value) => ({ value, label: `${value}日` }))} />
          <Button icon={<ReloadOutlined />} loading={query.isFetching} onClick={() => query.refetch()}>刷新</Button>
        </Space>
      </div>
      {query.isError ? <Alert showIcon type="warning" message="工作台数据加载失败" description="已保留最后一次成功的数据，请点击刷新重试。" /> : null}
      <MetricGrid overview={overview} />
      <div className="dashboard-main-grid">
        <TaskCenter overview={overview} />
        <TrendChart overview={{ ...overview, trend: overview.trend.slice(-rangeDays) }} rangeDays={rangeDays} onRangeChange={setRangeDays} />
      </div>
      <div className="dashboard-table-grid">
        <RecentOrders overview={overview} />
        <InventoryAlerts overview={overview} />
      </div>
      <RankPanels overview={overview} />
      <div className="dashboard-bottom-grid">
        <QuickActions />
        <SystemStatus overview={overview} />
      </div>
    </div>
  );
}
