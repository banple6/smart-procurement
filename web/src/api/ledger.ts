import { downloadBlob, getJson, saveBlob } from './client';

export interface LedgerFilters {
  start_date?: string;
  end_date?: string;
  unit_id?: string;
  status?: string;
  product?: string;
  order_no?: string;
}

export const ledgerApi = {
  list(filters?: LedgerFilters) {
    return getJson<Record<string, unknown>[]>('/admin/ledger', { params: filters });
  },
  async exportLedger(filters?: LedgerFilters) {
    saveBlob(await downloadBlob('/admin/ledger/export.xlsx', filters as Record<string, unknown> | undefined), '订单台账.xlsx');
  },
  preparation(params?: { business_date?: string; scope?: string; category?: string; page?: number; page_size?: number }) {
    return getJson<{ items: Record<string, unknown>[]; total: number; page: number; page_size: number }>('/admin/preparation-summary', { params });
  },
  async exportPreparation(params?: { business_date?: string; scope?: string; category?: string }) {
    saveBlob(await downloadBlob('/admin/preparation-summary/export.xlsx', params), '今日备货.xlsx');
  },
  deliverySheets(params?: { business_date?: string; status?: string; unit_id?: string }) {
    return getJson<{ business_date: string; units: Record<string, unknown>[] }>('/admin/delivery-sheets', { params });
  },
  async exportDeliverySheets(params?: { business_date?: string; status?: string; unit_id?: string }) {
    saveBlob(await downloadBlob('/admin/delivery-sheets/export.xlsx', params), '单位配送单.xlsx');
  },
};
