import type { Product } from '@/types/domain';

export function numericQuantity(value: string | number | undefined): number {
  const num = typeof value === 'number' ? value : Number(value || 0);
  return Number.isFinite(num) ? num : 0;
}

export function availableQuantity(product: Product): number {
  if (product.available_quantity !== undefined) return numericQuantity(product.available_quantity);
  return Math.max(0, numericQuantity(product.stock_quantity) - numericQuantity(product.reserved_quantity));
}

export function canAddToCart(product: Product, quantity: number): boolean {
  if (!product.active || product.is_deleted) return false;
  if (product.supply_status === 'paused' || product.supply_status === 'off_shelf') return false;
  if (availableQuantity(product) <= 0) return false;
  if (quantity < numericQuantity(product.min_order_quantity)) return false;
  return quantity <= availableQuantity(product);
}
