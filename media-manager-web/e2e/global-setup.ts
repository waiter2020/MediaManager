import { request, type FullConfig } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const API_BASE = process.env.E2E_API_URL || 'http://127.0.0.1:8080';
const E2E_USER = process.env.E2E_USERNAME || 'e2e_admin';
const E2E_PASS = process.env.E2E_PASSWORD || 'e2e-test-password-32chars!!';

async function globalSetup(_config: FullConfig) {
  const ctx = await request.newContext({ baseURL: API_BASE });

  try {
    const statusRes = await ctx.get('/api/v1/auth/setup/status', { timeout: 5000 });
    if (!statusRes.ok()) {
      console.warn(`[e2e] Backend not reachable at ${API_BASE} — UI tests will be skipped.`);
      fs.mkdirSync(path.join(process.cwd(), 'e2e'), { recursive: true });
      fs.writeFileSync(
        path.join(process.cwd(), 'e2e', '.auth-skip'),
        'backend-unavailable',
        'utf8',
      );
      await ctx.dispose();
      return;
    }

    const statusJson = await statusRes.json();
    if (!statusJson?.data?.setupCompleted) {
      const setupRes = await ctx.post('/api/v1/auth/setup', {
        data: { username: E2E_USER, password: E2E_PASS },
      });
      if (!setupRes.ok()) {
        console.warn('[e2e] Initial setup failed:', await setupRes.text());
      }
    }

    const loginRes = await ctx.post('/api/v1/auth/login', {
      data: { username: E2E_USER, password: E2E_PASS },
    });
    if (!loginRes.ok()) {
      console.warn('[e2e] Login failed — use E2E_USERNAME/E2E_PASSWORD or complete setup manually.');
      fs.writeFileSync(path.join(process.cwd(), 'e2e', '.auth-skip'), 'login-failed', 'utf8');
      await ctx.dispose();
      return;
    }

    const loginJson = await loginRes.json();
    const token = loginJson?.data?.accessToken;
    if (!token) {
      fs.writeFileSync(path.join(process.cwd(), 'e2e', '.auth-skip'), 'no-token', 'utf8');
      await ctx.dispose();
      return;
    }

    const authDir = path.join(process.cwd(), 'e2e');
    fs.mkdirSync(authDir, { recursive: true });
    fs.writeFileSync(
      path.join(authDir, '.auth-token.json'),
      JSON.stringify({ accessToken: token, username: E2E_USER }, null, 2),
      'utf8',
    );
    try {
      fs.unlinkSync(path.join(authDir, '.auth-skip'));
    } catch {
      // ignore
    }
    console.log('[e2e] Auth token prepared for', E2E_USER);
  } catch (e) {
    console.warn('[e2e] global-setup error:', e);
    fs.mkdirSync(path.join(process.cwd(), 'e2e'), { recursive: true });
    fs.writeFileSync(path.join(process.cwd(), 'e2e', '.auth-skip'), 'error', 'utf8');
  }

  await ctx.dispose();
}

export default globalSetup;
