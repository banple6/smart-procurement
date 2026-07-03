import { getJson } from './client';
import type { DashboardData, DashboardOverview } from '@/types/domain';

export const dashboardApi = {
  get() {
    return getJson<DashboardData>('/admin/dashboard');
  },
  overview(params?: { business_date?: string; range_days?: number }) {
    return getJson<DashboardOverview>('/admin/dashboard/overview', { params });
  },
  orderSummary() {
    return getJson<Array<{ product_id: string; name: string; unit: string; quantity: number }>>('/admin/order-summary');
  },
};
