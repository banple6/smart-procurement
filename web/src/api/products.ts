import { deleteJson, getJson, patchJson, postJson, putJson, apiClient } from './client';
import type { Product } from '@/types/domain';

export interface ProductFilters {
  q?: string;
  category?: string;
}

export type ProductCreatePayload = Omit<Product, 'id' | 'reserved_quantity' | 'available_quantity' | 'is_deleted' | 'updated_at'> & {
  reserved_quantity?: string;
};

export const productsApi = {
  list(filters?: ProductFilters) {
    return getJson<Product[]>('/products', { params: filters });
  },
  detail(productId: string) {
    return getJson<Product>(`/products/${productId}`);
  },
  create(payload: Partial<ProductCreatePayload>) {
    return postJson<Product>('/admin/products', payload);
  },
  update(productId: string, payload: Partial<Product>) {
    return putJson<Product>(`/admin/products/${productId}`, payload);
  },
  uploadImage(productId: string, file: File) {
    const form = new FormData();
    form.append('file', file);
    return apiClient.post<Product>(`/admin/products/${productId}/image`, form).then((r) => r.data);
  },
  patchStatus(productId: string, payload: { supply_status: string; active: boolean }) {
    return patchJson<Product>(`/admin/products/${productId}/status`, payload);
  },
  restore(productId: string) {
    return postJson<Product>(`/admin/products/${productId}/restore`);
  },
  changePrice(productId: string, payload: { price_cents: number; reason: string; expected_updated_at?: string }) {
    return patchJson<Product>(`/admin/products/${productId}/price`, payload);
  },
  setStock(productId: string, payload: { stock_quantity: string; detail: string }) {
    return patchJson<Product>(`/admin/products/${productId}/stock`, payload);
  },
  adjustInventory(productId: string, payload: { mode: 'set' | 'increase' | 'decrease'; quantity: string; reason: string; expected_updated_at?: string }) {
    return postJson<{ product: Product }>(`/admin/products/${productId}/inventory-adjust`, payload);
  },
  remove(productId: string) {
    return deleteJson<Product>(`/admin/products/${productId}`);
  },
};
