import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import type { CartLine, Product } from '@/types/domain';
import { availableQuantity, numericQuantity } from '@/utils/inventory';

interface CartContextValue {
  lines: CartLine[];
  note: string;
  setNote: (note: string) => void;
  setQuantity: (product: Product, quantity: number) => void;
  remove: (productId: string) => void;
  clear: () => void;
  totalCents: number;
}

const CartContext = createContext<CartContextValue | null>(null);

function normalizeQuantity(product: Product, requested: number): number {
  const min = numericQuantity(product.min_order_quantity);
  const step = numericQuantity(product.quantity_step) || 1;
  const available = availableQuantity(product);
  if (requested <= 0) return 0;
  const bounded = Math.min(requested, available);
  const steps = Math.floor((bounded - min) / step);
  if (bounded < min) return min <= available ? min : 0;
  return Number((min + Math.max(0, steps) * step).toFixed(3));
}

export function CartProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<CartLine[]>([]);
  const [note, setNote] = useState('');

  const setQuantity = (product: Product, quantity: number) => {
    const nextQuantity = normalizeQuantity(product, quantity);
    setLines((current) => {
      const existing = current.find((line) => line.product.id === product.id);
      if (nextQuantity <= 0) return current.filter((line) => line.product.id !== product.id);
      if (existing) {
        return current.map((line) => (line.product.id === product.id ? { product, quantity: nextQuantity } : line));
      }
      return [...current, { product, quantity: nextQuantity }];
    });
  };

  const value = useMemo<CartContextValue>(
    () => ({
      lines,
      note,
      setNote,
      setQuantity,
      remove: (productId) => setLines((current) => current.filter((line) => line.product.id !== productId)),
      clear: () => {
        setLines([]);
        setNote('');
      },
      totalCents: lines.reduce((sum, line) => sum + Math.round(line.product.price_cents * line.quantity), 0),
    }),
    [lines, note],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const value = useContext(CartContext);
  if (!value) throw new Error('useCart must be used inside CartProvider');
  return value;
}
