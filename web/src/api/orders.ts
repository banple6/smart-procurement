import { apiClient, getJson, patchJson, postJson, putJson } from './client';
import type { Order, PageResult, ReceiptIssue } from '@/types/domain';

export interface OrderFilters {
  status?: string;
  unit_id?: string;
  start_date?: string;
  end_date?: string;
  order_no?: string;
  page?: number;
  page_size?: number;
}

export const ordersApi = {
  listMine(filters?: OrderFilters) {
    return getJson<PageResult<Order>>('/orders', { params: filters });
  },
  listAdmin(filters?: OrderFilters) {
    return getJson<PageResult<Order>>('/admin/orders', { params: filters });
  },
  detail(orderId: string, admin = false) {
    return getJson<Order>(admin ? `/admin/orders/${orderId}` : `/orders/${orderId}`);
  },
  create(payload: { note: string; items: Array<{ product_id: string; quantity: string }>; client_request_id?: string }) {
    return postJson<Order>('/orders', payload);
  },
  updatePending(orderId: string, payload: { note: string; items: Array<{ product_id: string; quantity: string }> }) {
    return putJson<Order>(`/orders/${orderId}`, payload);
  },
  cancel(orderId: string) {
    return postJson<Order>(`/orders/${orderId}/cancel`);
  },
  confirmReceipt(orderId: string) {
    return postJson<Order>(`/orders/${orderId}/confirm-receipt`);
  },
  reorderPreview(orderId: string) {
    return getJson<{ items: unknown[]; unavailable_items: unknown[] }>(`/orders/${orderId}/reorder-preview`);
  },
  adminStatus(orderId: string, status: string) {
    return patchJson<Order>(`/admin/orders/${orderId}/status`, { status });
  },
  adjustItem(orderId: string, itemId: string, payload: { actual_quantity: string; reason: string; expected_updated_at?: string }) {
    return patchJson<Order>(`/admin/orders/${orderId}/items/${itemId}/actual-quantity`, payload);
  },
  ship(orderId: string, payload: { files: File[]; note: string }) {
    const form = new FormData();
    payload.files.forEach((file) => form.append('photos', file));
    form.append('note', payload.note);
    form.append('request_id', crypto.randomUUID());
    return apiClient.post<Order>(`/admin/orders/${orderId}/ship`, form).then((r) => r.data);
  },
  createReceiptIssue(orderId: string, payload: { issue_type: string; description: string; files: File[] }) {
    const form = new FormData();
    form.append('issue_type', payload.issue_type);
    form.append('description', payload.description);
    payload.files.forEach((file) => form.append('photos', file));
    return apiClient.post<ReceiptIssue>(`/orders/${orderId}/receipt-issues`, form).then((r) => r.data);
  },
  listReceiptIssues(orderId: string) {
    return getJson<ReceiptIssue[]>(`/orders/${orderId}/receipt-issues`);
  },
};
