import { request } from '@umijs/max';

export interface GetItemsParams {
  libraryId?: number;
  type?: string;
  keyword?: string;
  categoryIds?: number[];
  tagIds?: number[];
  page?: number;
  size?: number;
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

export async function getItems(params: GetItemsParams) {
  return request('/api/v1/items', { method: 'GET', params });
}

export async function getItem(id: number) {
  return request(`/api/v1/items/${id}`, { method: 'GET' });
}

export async function getItemDetail(id: number) {
  return request(`/api/v1/items/${id}/detail`, { method: 'GET' });
}

export async function updateMetadata(id: number, data: any) {
  return request(`/api/v1/items/${id}/metadata`, { method: 'PUT', data });
}

export async function refreshMetadata(id: number) {
  return request(`/api/v1/items/${id}/refresh`, { method: 'POST' });
}

export async function identifyItem(id: number, data: { provider?: string; externalId: string }) {
  return request(`/api/v1/items/${id}/identify`, { method: 'POST', data });
}

export async function searchTmdbCandidates(id: number, q: string) {
  return request(`/api/v1/items/${id}/tmdb/search`, { method: 'GET', params: { q } });
}

export async function deleteItem(id: number) {
  return request(`/api/v1/items/${id}`, { method: 'DELETE' });
}

export async function deleteSourceFile(id: number) {
  return request(`/api/v1/items/${id}/file`, { method: 'DELETE' });
}

export async function getFilters() {
  return request('/api/v1/items/filters', { method: 'GET' });
}
