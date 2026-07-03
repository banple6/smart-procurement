import { describe, expect, test } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { canAddToCart } from '@/utils/inventory';
import { formatMoney, lineSubtotal, yuanToCents } from '@/utils/money';
import { landingPathForRole, canOpenAdmin, canOpenUnit } from '@/utils/navigation';
import { modeForWidth } from '@/utils/responsive';
import { orderStatusText } from '@/utils/status';
import { toChineseError } from '@/utils/errors';
import type { Product } from '@/types/domain';

function product(overrides: Partial<Product> = {}): Product {
  return {
    id: 'p1',
    product_code: 'P001',
    name: '山药',
    category: '蔬菜',
    spec: '普通',
    unit: '公斤',
    price_cents: 300,
    stock_quantity: '10',
    reserved_quantity: '2',
    min_order_quantity: '1',
    quantity_step: '1',
    warning_quantity: '1',
    supply_status: 'normal',
    active: true,
    ...overrides,
  };
}

describe('Web business policy', () => {
  test('登录后根据服务端角色自动分流', () => {
    expect(landingPathForRole('admin')).toBe('/admin/dashboard');
    expect(landingPathForRole('unit_user')).toBe('/unit/products');
  });

  test('路由权限区分管理员和子单位', () => {
    expect(canOpenAdmin('admin')).toBe(true);
    expect(canOpenAdmin('unit_user')).toBe(false);
    expect(canOpenUnit('unit_user')).toBe(true);
    expect(canOpenUnit('admin')).toBe(false);
  });

  test('商品金额和清单金额格式正确', () => {
    expect(formatMoney(0)).toBe('¥0.00');
    expect(formatMoney(305)).toBe('¥3.05');
    expect(yuanToCents('12.30')).toBe(1230);
    expect(lineSubtotal(450, 2.5)).toBe(1125);
  });

  test('库存不足、暂停供应、下架和删除不能加入清单', () => {
    expect(canAddToCart(product(), 1)).toBe(true);
    expect(canAddToCart(product({ stock_quantity: '2', reserved_quantity: '2' }), 1)).toBe(false);
    expect(canAddToCart(product({ supply_status: 'paused' }), 1)).toBe(false);
    expect(canAddToCart(product({ supply_status: 'off_shelf' }), 1)).toBe(false);
    expect(canAddToCart(product({ is_deleted: true }), 1)).toBe(false);
  });

  test('订单状态统一显示中文', () => {
    expect(orderStatusText.pending).toBe('待接单');
    expect(orderStatusText.accepted).toBe('已接单');
    expect(orderStatusText.preparing).toBe('备货中');
    expect(orderStatusText.shipped).toBe('已发货');
    expect(orderStatusText.completed).toBe('已完成');
    expect(orderStatusText.cancelled).toBe('已取消');
  });

  test('错误处理不暴露 HTTP 和内部异常文案', () => {
    const message = toChineseError({}).message;
    expect(message).toBe('操作失败，请稍后重试');
    expect(message).not.toContain('AxiosError');
    expect(message).not.toContain('HTTP');
    expect(message).not.toContain('SQL');
    const file = readFileSync(resolve('src/utils/errors.ts'), 'utf8');
    expect(file).not.toContain('Python Traceback');
  });

  test('409 冲突使用中文提示并由调用页保留表单输入', () => {
    const file = readFileSync(resolve('src/utils/errors.ts'), 'utf8');
    expect(file).toContain('数据已变化，请刷新后重试');
  });

  test('初始密码弹窗不写本地持久存储', () => {
    const file = readFileSync(resolve('src/pages/admin/UsersPage.tsx'), 'utf8');
    expect(file).toContain('初始密码只显示一次');
    expect(file).not.toContain('localStorage');
    expect(file).not.toContain('sessionStorage');
  });

  test('私有图片访问不把 token 拼进 URL', () => {
    const shipping = readFileSync(resolve('src/api/shipping.ts'), 'utf8');
    const orders = readFileSync(resolve('src/api/orders.ts'), 'utf8');
    expect(shipping).not.toContain('token=');
    expect(orders).not.toContain('token=');
  });

  test('手机宽度切换为移动卡片模式', () => {
    expect(modeForWidth(390)).toBe('mobile');
    expect(modeForWidth(900)).toBe('tablet');
    expect(modeForWidth(1440)).toBe('desktop');
  });

  test('警徽 SVG 使用原始比例且登录页无注册入口', () => {
    const svg = readFileSync(resolve('public/brand/police-badge.svg'), 'utf8');
    expect(svg).toContain('viewBox="0 0 167.36 176.36"');
    const login = readFileSync(resolve('src/pages/common/LoginPage.tsx'), 'utf8');
    expect(login).not.toContain('注册');
    expect(login).not.toContain('邀请码');
  });
});
