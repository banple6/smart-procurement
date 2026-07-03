import { getJson, postJson } from './client';
import type { ReceiptIssue } from '@/types/domain';

export const receiptIssuesApi = {
  list(status?: string) {
    return getJson<ReceiptIssue[]>('/admin/receipt-issues', { params: { status } });
  },
  resolve(issueId: string, resolution_note: string) {
    return postJson<ReceiptIssue>(`/admin/receipt-issues/${issueId}/resolve`, { resolution_note });
  },
};
