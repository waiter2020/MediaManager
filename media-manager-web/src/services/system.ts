import { request } from '@umijs/max';

export async function checkSetup() {
  return request('/api/v1/system/status', { method: 'GET' });
}

export async function setupSystem(data: any) {
  return request('/api/v1/auth/setup', { method: 'POST', data });
}

export async function login(data: any) {
  return request('/api/v1/auth/login', { method: 'POST', data });
}

export async function getDashboardStats() {
  return request('/api/v1/system/status', { method: 'GET' });
}

export async function getSystemInfo() {
  return request('/api/v1/system/info', { method: 'GET' });
}

export async function getScanStatus() {
  return request('/api/v1/system/scan/status', { method: 'GET' });
}

export async function getDirectories(path?: string) {
  return request('/api/v1/system/directories', {
    method: 'GET',
    params: { path },
  });
}
