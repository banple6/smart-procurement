import { downloadBlob } from './client';

export const shippingApi = {
  photo(orderId: string, photoId: string) {
    return downloadBlob(`/orders/${orderId}/shipping-photos/${photoId}`);
  },
  receiptIssuePhoto(orderId: string, issueId: string, photoId: string) {
    return downloadBlob(`/orders/${orderId}/receipt-issues/${issueId}/photos/${photoId}`);
  },
};
