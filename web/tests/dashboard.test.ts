import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, test } from 'vitest';
import { dashboardMetricDefinitions, describeSeconds, taskTargetForType } from '@/utils/dashboard';

describe('Desktop dashboard policy', () => {
  test('管理员工作台提供 8 个可点击指标和真实目标路径', () => {
    expect(dashboardMetricDefinitions.map((item) => item.key)).toEqual([
      'today_valid_orders',
      'today_total_cents',
      'pending',
      'preparing',
      'waiting_shipment',
      'waiting_receipt',
      'open_receipt_issues',
      'tight_inventory',
    ]);
    expect(dashboardMetricDefinitions.find((item) => item.key === 'pending')?.target).toBe('/admin/orders?status=pending');
    expect(dashboardMetricDefinitions.find((item) => item.key === 'tight_inventory')?.target).toBe('/admin/products?status=tight');
    expect(dashboardMetricDefinitions.find((item) => item.key === 'open_receipt_issues')?.target).toBe('/admin/receipt-issues?status=open');
  });

  test('待办入口使用项目真实路由', () => {
    expect(taskTargetForType('pending_orders')).toBe('/admin/orders?status=pending');
    expect(taskTargetForType('waiting_shipment')).toBe('/admin/orders?status=preparing');
    expect(taskTargetForType('tight_inventory')).toBe('/admin/products?status=tight');
    expect(taskTargetForType('cutoff')).toBe('/admin/settings');
    expect(taskTargetForType('unknown')).toBe('/admin/dashboard');
  });

  test('等待时间使用中文说明且不暴露技术单位', () => {
    expect(describeSeconds(90)).toBe('1 分钟');
    expect(describeSeconds(3660)).toBe('1 小时 1 分钟');
    expect(describeSeconds(null)).toBe('暂无等待');
  });

  test('工作台源文件包含桌面后台模块且不展示警徽', () => {
    const dashboard = readFileSync(resolve('src/pages/admin/DashboardPage.tsx'), 'utf8');
    expect(dashboard).toContain('待办中心');
    expect(dashboard).toContain('近 7 日采购趋势');
    expect(dashboard).toContain('最近订单');
    expect(dashboard).toContain('库存预警');
    expect(dashboard).toContain('今日需求排行');
    expect(dashboard).toContain('今日单位采购情况');
    expect(dashboard).not.toContain('PoliceBadge');
    expect(dashboard).not.toContain('police-badge');
  });
});
