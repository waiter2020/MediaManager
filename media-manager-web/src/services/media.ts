import { request } from '@umijs/max';

export interface GetItemsParams {
  libraryId?: number;
  type?: string;
  keyword?: string;
  categoryIds?: number[]; // Array for URL multi-params
  tagIds?: number[];      // Array for URL multi-params
  page?: number;
  size?: number;
}

export async function getItems(params: GetItemsParams) {
  return request('/api/v1/items', { method: 'GET', params });
}

export async function getItem(id: number) {
  return request(`/api/v1/items/${id}`, { method: 'GET' });
}

export async function updateMetadata(id: number, data: any) {
  return request(`/api/v1/items/${id}/metadata`, { method: 'PUT', data });
}

export async function refreshMetadata(id: number) {
  return request(`/api/v1/items/${id}/refresh`, { method: 'POST' });
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
