import type { DashboardMetricKey, DashboardTaskType } from '@/types/domain';

export const dashboardMetricDefinitions: Array<{
  key: DashboardMetricKey;
  title: string;
  description: string;
  target: string;
  tone: 'normal' | 'warning' | 'danger';
}> = [
  { key: 'today_valid_orders', title: '今日有效订单', description: '不含已取消订单', target: '/admin/orders?date=today', tone: 'normal' },
  { key: 'today_total_cents', title: '今日采购金额', description: '按订单金额快照统计', target: '/admin/ledger', tone: 'normal' },
  { key: 'pending', title: '待接单', description: '需要管理员接单', target: '/admin/orders?status=pending', tone: 'danger' },
  { key: 'preparing', title: '备货中', description: '已接单和备货中订单', target: '/admin/orders?status=preparing', tone: 'warning' },
  { key: 'waiting_shipment', title: '待发货', description: '备货中等待发货', target: '/admin/orders?status=preparing', tone: 'warning' },
  { key: 'waiting_receipt', title: '待确认收货', description: '子单位尚未确认', target: '/admin/orders?status=shipped', tone: 'normal' },
  { key: 'open_receipt_issues', title: '待处理异常', description: '收货异常未处理', target: '/admin/receipt-issues?status=open', tone: 'danger' },
  { key: 'tight_inventory', title: '库存预警', description: '可用库存低于预警', target: '/admin/products?status=tight', tone: 'warning' },
];

export function taskTargetForType(type: DashboardTaskType | string): string {
  if (type === 'receipt_issues') return '/admin/receipt-issues?status=open';
  if (type === 'pending_orders') return '/admin/orders?status=pending';
  if (type === 'waiting_shipment') return '/admin/orders?status=preparing';
  if (type === 'tight_inventory') return '/admin/products?status=tight';
  if (type === 'cutoff') return '/admin/settings';
  return '/admin/dashboard';
}

export function describeSeconds(seconds: number | null | undefined): string {
  if (!seconds || seconds <= 0) return '暂无等待';
  const minutes = Math.max(1, Math.floor(seconds / 60));
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  if (!hours) return `${minutes} 分钟`;
  if (!remainingMinutes) return `${hours} 小时`;
  return `${hours} 小时 ${remainingMinutes} 分钟`;
}

export function formatPercent(value: number | null | undefined): string {
  const numeric = Number(value ?? 0);
  if (!Number.isFinite(numeric) || numeric === 0) return '持平';
  return `${numeric > 0 ? '+' : ''}${numeric.toFixed(1)}%`;
}
