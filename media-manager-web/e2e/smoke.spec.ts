import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const skipFile = path.join(__dirname, '.auth-skip');
const tokenFile = path.join(__dirname, '.auth-token.json');

function shouldSkip(): boolean {
  return fs.existsSync(skipFile);
}

function loadToken(): string | null {
  if (!fs.existsSync(tokenFile)) return null;
  try {
    const data = JSON.parse(fs.readFileSync(tokenFile, 'utf8'));
    return data.accessToken ?? null;
  } catch {
    return null;
  }
}

test.describe('MediaManager smoke', () => {
  test.beforeEach(() => {
    test.skip(shouldSkip(), 'Backend unavailable or auth failed — start API on :8080 first');
  });

  test('API health', async ({ request }) => {
    const apiBase = process.env.E2E_API_URL || 'http://127.0.0.1:8080';
    const res = await request.get(`${apiBase}/api/v1/system/status`);
    expect(res.ok()).toBeTruthy();
    const json = await res.json();
    expect(json.code).toBe(200);
  });

  test('login page renders', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByTestId('login-username')).toBeVisible();
    await expect(page.getByTestId('login-password')).toBeVisible();
    await expect(page.getByRole('button', { name: '登录' })).toBeVisible();
  });

  test('authenticated dashboard', async ({ page }) => {
    const token = loadToken();
    test.skip(!token, 'No auth token from global-setup');

    await page.addInitScript((accessToken) => {
      localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto('/dashboard');
    await expect(page.getByRole('heading', { name: '仪表盘' })).toBeVisible({ timeout: 15000 });
  });

  test('browse page loads', async ({ page }) => {
    const token = loadToken();
    test.skip(!token, 'No auth token from global-setup');

    await page.addInitScript((accessToken) => {
      localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto('/browse');
    await expect(page.locator('body')).toContainText(/媒体|浏览|暂无/, { timeout: 15000 });
  });
});
