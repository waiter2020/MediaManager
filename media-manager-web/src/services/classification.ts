import { request } from '@umijs/max';

export async function getTags() {
  return request('/api/v1/tags', { method: 'GET' });
}

export async function getCategoryTree() {
  return request('/api/v1/categories', { method: 'GET' });
}

export async function createTag(data: { name: string; color?: string }) {
  return request('/api/v1/tags', { method: 'POST', data });
}

export async function updateTag(id: number, data: { name?: string; color?: string }) {
  return request(`/api/v1/tags/${id}`, { method: 'PUT', data });
}

export async function deleteTag(id: number) {
  return request(`/api/v1/tags/${id}`, { method: 'DELETE' });
}

export async function addTagToItem(itemId: number, tagId: number) {
  return request(`/api/v1/tags/items/${itemId}?tagId=${tagId}`, { method: 'POST' });
}

export async function removeTagFromItem(itemId: number, tagId: number) {
  return request(`/api/v1/tags/items/${itemId}/${tagId}`, { method: 'DELETE' });
}

export async function createCategory(data: { name: string; parentId?: number; type?: string }) {
  return request('/api/v1/categories', { method: 'POST', data });
}

export async function updateCategory(id: number, data: { name?: string; parentId?: number; type?: string }) {
  return request(`/api/v1/categories/${id}`, { method: 'PUT', data });
}

export async function deleteCategory(id: number) {
  return request(`/api/v1/categories/${id}`, { method: 'DELETE' });
}
