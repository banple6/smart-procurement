export function formatMoney(cents: number | string | null | undefined): string {
  const value = typeof cents === 'string' ? Number(cents) : cents ?? 0;
  return `¥${(Number.isFinite(value) ? value / 100 : 0).toFixed(2)}`;
}

export function yuanToCents(value: string): number {
  const normalized = value.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) {
    throw new Error('金额格式不正确');
  }
  const [yuan, cents = ''] = normalized.split('.');
  return Number(yuan) * 100 + Number(cents.padEnd(2, '0'));
}

export function lineSubtotal(priceCents: number, quantity: number): number {
  return Math.round(priceCents * quantity);
}
