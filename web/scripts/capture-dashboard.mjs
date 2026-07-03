import { chromium } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';

const target = process.argv[2] || 'http://127.0.0.1:4173/admin/dashboard';
const outDir = resolve(process.argv[3] || '../artifacts/dashboard-after');
const scenario = process.argv[4] || 'normal';

const adminUser = {
  id: 'test-admin',
  username: 'TEST_admin',
  display_name: 'TEST 管理员',
  role: 'admin',
  unit_id: '',
  unit_code: '',
  unit_name: '',
  default_delivery_point: '',
  active: true,
  must_change_password: false,
};

const recentOrders = Array.from({ length: 10 }).map((_, index) => ({
  id: `order-${index + 1}`,
  order_no: `SP20260703-${String(index + 1).padStart(6, '0')}`,
  unit_id: index % 2 ? 'u2' : 'u1',
  unit_name_snapshot: index % 2 ? '第三食堂' : '第一食堂',
  delivery_point_snapshot: index % 2 ? '三号点' : '第一食堂收货点',
  status: ['pending', 'accepted', 'preparing', 'shipped', 'completed'][index % 5],
  total_cents: 28650 + index * 1200,
  created_at: `2026-07-03 ${String(8 + index).padStart(2, '0')}:28:00`,
  item_count: 3 + (index % 5),
  open_receipt_issue_count: index === 2 ? 1 : 0,
}));

const overview = {
  business_date: '2026-07-03',
  server_now: '2026-07-03T15:30:12+08:00',
  refreshed_at: '2026-07-03T15:30:12+08:00',
  metrics: {
    today_valid_orders: scenario === 'empty' ? 0 : 18,
    today_total_cents: scenario === 'empty' ? 0 : 1268000,
    pending: scenario === 'empty' ? 0 : 3,
    preparing: scenario === 'empty' ? 0 : 4,
    waiting_shipment: scenario === 'empty' ? 0 : 2,
    waiting_receipt: scenario === 'empty' ? 0 : 5,
    open_receipt_issues: scenario === 'empty' ? 0 : 2,
    tight_inventory: scenario === 'empty' ? 0 : 5,
  },
  comparisons: { orders_vs_yesterday_percent: 8.2, amount_vs_yesterday_percent: -3.4 },
  tasks: [
    { type: 'receipt_issues', title: '收货异常', count: scenario === 'empty' ? 0 : 2, priority: scenario === 'empty' ? 'normal' : 'critical', description: '其中 1 笔为品质问题', action_label: '查看异常', target_url: '/admin/receipt-issues?status=open' },
    { type: 'pending_orders', title: '待接单订单', count: scenario === 'empty' ? 0 : 3, oldest_wait_seconds: 2520, priority: scenario === 'empty' ? 'normal' : 'high', description: '最早一笔已等待 42 分钟', action_label: '立即处理', target_url: '/admin/orders?status=pending' },
    { type: 'waiting_shipment', title: '等待发货', count: scenario === 'empty' ? 0 : 2, priority: scenario === 'empty' ? 'normal' : 'high', description: '备货完成后请确认发货', action_label: '确认发货', target_url: '/admin/orders?status=preparing' },
    { type: 'tight_inventory', title: '库存预警', count: scenario === 'empty' ? 0 : 5, priority: scenario === 'empty' ? 'normal' : 'warning', description: '山药库存需要关注', action_label: '查看库存', target_url: '/admin/products?status=tight' },
    { type: 'cutoff', title: '今日截止时间', count: 1, priority: 'normal', description: '2026-07-03 16:00', action_label: '调整时间', target_url: '/admin/settings' },
  ],
  trend: Array.from({ length: 7 }).map((_, index) => ({
    date: new Date(Date.UTC(2026, 6, 3 - (6 - index))).toISOString().slice(0, 10),
    order_count: scenario === 'empty' ? 0 : 8 + index,
    amount_cents: scenario === 'empty' ? 0 : 420000 + index * 85000,
  })),
  recent_orders: scenario === 'empty' ? [] : recentOrders,
  inventory_alerts: scenario === 'empty' ? [] : [
    { id: 'p1', name: '山药', unit: '公斤', stock_quantity: '20', reserved_quantity: '18', available_quantity: 2, warning_quantity: '5', supply_status: 'tight', active: true },
    { id: 'p2', name: '西红柿', unit: '公斤', stock_quantity: '0', reserved_quantity: '0', available_quantity: 0, warning_quantity: '3', supply_status: 'normal', active: true },
    { id: 'p3', name: '土豆', unit: '公斤', stock_quantity: '12', reserved_quantity: '9', available_quantity: 3, warning_quantity: '5', supply_status: 'normal', active: true },
  ],
  demand_rank: scenario === 'empty' ? [] : [
    { product_id: 'p1', name: '西红柿', quantity: 86, unit: '公斤', unit_count: 5, order_count: 9 },
    { product_id: 'p2', name: '土豆', quantity: 62, unit: '公斤', unit_count: 4, order_count: 7 },
  ],
  unit_rank: scenario === 'empty' ? [] : [
    { unit_id: 'u1', unit_name: '第一食堂', order_count: 8, amount_cents: 486000, product_count: 16, issue_count: 1 },
    { unit_id: 'u2', unit_name: '第三食堂', order_count: 6, amount_cents: 392000, product_count: 12, issue_count: 0 },
  ],
  system_status: {
    service: 'ok',
    last_backup_at: '2026-07-03T02:00:00+08:00',
    disk_usage_percent: 32,
    version: 'Web 1.1.0',
    refreshed_at: '2026-07-03T15:30:12+08:00',
  },
};

const legacyDashboard = {
  today_orders: overview.metrics.today_valid_orders,
  today_total_cents: overview.metrics.today_total_cents,
  pending: overview.metrics.pending,
  preparing: overview.metrics.preparing,
  shipped: overview.metrics.waiting_receipt,
  tight_inventory: overview.metrics.tight_inventory,
  open_receipt_issues: overview.metrics.open_receipt_issues,
  recent_orders: overview.recent_orders.slice(0, 5),
  demand_rank: overview.demand_rank.slice(0, 5),
};

const viewports = [
  ['dashboard-1920.png', { width: 1920, height: 1080 }],
  ['dashboard-1440.png', { width: 1440, height: 900 }],
  ['dashboard-1366.png', { width: 1366, height: 768 }],
  ['dashboard-1280.png', { width: 1280, height: 800 }],
  ['dashboard-1024.png', { width: 1024, height: 768 }],
];

await mkdir(outDir, { recursive: true });
let browser;
try {
  browser = await chromium.launch({ channel: 'chrome' });
} catch {
  browser = await chromium.launch();
}
try {
  for (const [filename, viewport] of viewports) {
    const page = await browser.newPage({ viewport });
    await page.route('**/api/v1/web-auth/me', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(adminUser) }));
    await page.route('**/api/v1/auth/me', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(adminUser) }));
    await page.route('**/api/v1/admin/dashboard/overview**', (route) => {
      if (scenario === 'partial-error') {
        return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ detail: '工作台数据加载失败' }) });
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(overview) });
    });
    await page.route('**/api/v1/admin/dashboard', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(legacyDashboard) }));
    await page.goto(target, { waitUntil: 'networkidle' });
    await page.screenshot({ path: resolve(outDir, filename), fullPage: true });
    await page.close();
  }
} finally {
  await browser.close();
}
