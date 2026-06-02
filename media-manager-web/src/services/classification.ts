import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';

export interface TagItem {
  id: number;
  name: string;
  color?: string;
  source?: string;
  createdAt?: string;
}

export interface CategoryItem {
  id: number;
  name: string;
  parentId?: number;
  type?: string;
  children?: CategoryItem[];
}

export interface TagPayload {
  name: string;
  color?: string;
}

export interface CategoryPayload {
  name: string;
  parentId?: number;
  type?: string;
}

export async function getTags() {
  return request<ApiResponse<TagItem[]>>('/api/v1/tags', { method: 'GET' });
}

export async function getCategoryTree() {
  return request<ApiResponse<CategoryItem[]>>('/api/v1/categories', { method: 'GET' });
}

export async function createTag(data: TagPayload) {
  return request<ApiResponse<TagItem>>('/api/v1/tags', { method: 'POST', data });
}

export async function updateTag(id: number, data: Partial<TagPayload>) {
  return request<ApiResponse<TagItem>>(`/api/v1/tags/${id}`, { method: 'PUT', data });
}

export async function deleteTag(id: number) {
  return request<ApiResponse<void>>(`/api/v1/tags/${id}`, { method: 'DELETE' });
}

export async function addTagToItem(itemId: number, tagId: number) {
  return request<ApiResponse<void>>(`/api/v1/tags/items/${itemId}?tagId=${tagId}`, {
    method: 'POST',
  });
}

export async function removeTagFromItem(itemId: number, tagId: number) {
  return request<ApiResponse<void>>(`/api/v1/tags/items/${itemId}/${tagId}`, {
    method: 'DELETE',
  });
}

export async function batchAddTags(mediaItemIds: number[], tagIds: number[]) {
  return request<ApiResponse<void>>('/api/v1/tags/batch', {
    method: 'POST',
    data: { mediaItemIds, tagIds },
  });
}

export async function createCategory(data: CategoryPayload) {
  return request<ApiResponse<CategoryItem>>('/api/v1/categories', { method: 'POST', data });
}

export async function updateCategory(id: number, data: Partial<CategoryPayload>) {
  return request<ApiResponse<CategoryItem>>(`/api/v1/categories/${id}`, { method: 'PUT', data });
}

export async function deleteCategory(id: number) {
  return request<ApiResponse<void>>(`/api/v1/categories/${id}`, { method: 'DELETE' });
}

/* ========== Library-level classification ========== */

export interface LibraryClassifyStatus {
  running?: boolean;
  libraryId?: number;
  processed?: number;
  failed?: number;
  total?: number;
}

export interface LibraryClassifyStartResult {
  accepted?: boolean;
  message?: string;
  status?: LibraryClassifyStatus;
}

export async function classifyLibrary(id: number) {
  return request<ApiResponse<LibraryClassifyStartResult>>(`/api/v1/libraries/${id}/classify`, {
    method: 'POST',
  });
}

export async function getLibraryClassifyStatus() {
  return request<ApiResponse<LibraryClassifyStatus>>('/api/v1/libraries/classify/status', {
    method: 'GET',
  });
}
