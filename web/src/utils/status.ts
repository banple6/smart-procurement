import type { OrderStatus, SupplyStatus } from '@/types/domain';

export const orderStatusText: Record<OrderStatus, string> = {
  pending: '待接单',
  accepted: '已接单',
  preparing: '备货中',
  shipped: '已发货',
  completed: '已完成',
  cancelled: '已取消',
};

export const supplyStatusText: Record<SupplyStatus, string> = {
  normal: '正常供应',
  tight: '库存紧张',
  paused: '暂停供应',
  off_shelf: '已下架',
};

export function nextAdminOrderAction(status: OrderStatus): { label: string; nextStatus?: OrderStatus; ship?: boolean } | null {
  if (status === 'pending') return { label: '接单', nextStatus: 'accepted' };
  if (status === 'accepted') return { label: '开始备货', nextStatus: 'preparing' };
  if (status === 'preparing') return { label: '确认发货', ship: true };
  if (status === 'shipped') return { label: '完成订单', nextStatus: 'completed' };
  return null;
}

export function roleText(role: string): string {
  return role === 'admin' ? '系统管理员' : '子单位账号';
}
