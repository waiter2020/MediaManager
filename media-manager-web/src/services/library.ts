import { request } from '@umijs/max';

export async function getLibraries() {
  return request('/api/v1/libraries', { method: 'GET' });
}

export async function getLibrary(id: number) {
  return request(`/api/v1/libraries/${id}`, { method: 'GET' });
}

export async function createLibrary(data: any) {
  return request('/api/v1/libraries', { method: 'POST', data });
}

export async function deleteLibrary(id: number) {
  return request(`/api/v1/libraries/${id}`, { method: 'DELETE' });
}

export async function triggerScan(id: number) {
  return request(`/api/v1/libraries/${id}/scan`, { method: 'POST' });
}

export async function updateLibrary(id: number, data: any) {
  return request(`/api/v1/libraries/${id}`, { method: 'PUT', data });
}

export async function getLibraryStats(id: number) {
  return request(`/api/v1/libraries/${id}/stats`, { method: 'GET' });
}
