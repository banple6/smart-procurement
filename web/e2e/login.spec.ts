import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/web-auth/me', async (route) => {
    await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ detail: '登录已过期，请重新登录' }) });
  });
  await page.route('**/api/v1/web-auth/qr/challenges', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        challenge_id: 'test-challenge',
        qr_svg_data_url: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNjAiIGhlaWdodD0iMjYwIj48cmVjdCB3aWR0aD0iMjYwIiBoZWlnaHQ9IjI2MCIgZmlsbD0iI2ZmZiIvPjx0ZXh0IHg9IjEzMCIgeT0iMTMwIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIj5URVNUPC90ZXh0Pjwvc3ZnPg==',
        qr_payload: 'jingrongxianpei://web-login?token=test',
        status: 'pending',
        expires_at: 120,
        server_now: 0,
      }),
    });
  });
  await page.route('**/api/v1/web-auth/qr/challenges/test-challenge/status', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ status: 'pending', expires_at: 120, server_now: 1 }) });
  });
});

test('登录页显示公安品牌且只提供扫码登录', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: '景荣鲜配' })).toBeVisible();
  await expect(page.getByText('XX公安局后勤食材采购配送系统')).toBeVisible();
  await expect(page.getByText('公安内部使用')).toBeVisible();
  await expect(page.getByRole('heading', { name: '扫码登录网页版' })).toBeVisible();
  await expect(page.getByAltText('网页登录二维码')).toBeVisible();
  await expect(page.getByRole('button', { name: '刷新二维码' })).toBeVisible();
  await expect(page.getByText('注册')).toHaveCount(0);
  await expect(page.getByText('邀请码')).toHaveCount(0);
  await expect(page.getByLabel('账号')).toHaveCount(0);
  await expect(page.getByLabel('密码')).toHaveCount(0);
});

test('手机浏览器登录页不横向溢出', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/login');
  const hasHorizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  expect(hasHorizontalOverflow).toBe(false);
});
