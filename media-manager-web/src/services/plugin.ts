import { request } from '@umijs/max';

export async function listPlugins() {
  return request('/api/v1/plugins', { method: 'GET' });
}

export async function listLibraryPlugins(libraryId: number) {
  return request(`/api/v1/libraries/${libraryId}/plugins`, { method: 'GET' });
}

export async function updateLibraryPlugins(libraryId: number, configs: Record<string, unknown>[]) {
  return request(`/api/v1/libraries/${libraryId}/plugins`, { method: 'PUT', data: configs });
}
