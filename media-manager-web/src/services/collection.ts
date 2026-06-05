import { request } from '@umijs/max';
import type { ApiResponse, PageResult } from '@/types/api';
import type { MediaItem } from '@/types/media';

export interface MediaCollection {
  id: number;
  ownerUserId?: number;
  ownerDisplayName?: string;
  name: string;
  description?: string;
  type: 'COLLECTION' | 'PLAYLIST';
  visibility: 'PRIVATE' | 'SHARED';
  smart?: boolean;
  rule?: CollectionRule;
  posterPath?: string;
  coverItem?: MediaItem;
  itemCount?: number;
  createdAt?: string;
  updatedAt?: string;
  items?: MediaItem[];
}

export interface CollectionRule {
  libraryId?: number;
  type?: string;
  keyword?: string;
  categoryIds?: number[];
  tagIds?: number[];
  minYear?: number;
  maxYear?: number;
  minRating?: number;
  sortField?: 'title' | 'releaseDate' | 'rating' | 'createdAt' | 'updatedAt';
  sortOrder?: 'ASC' | 'DESC';
  limit?: number;
  unwatchedOnly?: boolean;
}

export interface CollectionPayload {
  name: string;
  description?: string;
  type?: 'COLLECTION' | 'PLAYLIST';
  visibility?: 'PRIVATE' | 'SHARED';
  smart?: boolean;
  rule?: CollectionRule;
  itemIds?: number[];
}

export async function listCollections() {
  return request<ApiResponse<MediaCollection[]>>('/api/v1/collections', { method: 'GET' });
}

export async function getCollection(id: number, includeItems = true) {
  return request<ApiResponse<MediaCollection>>(`/api/v1/collections/${id}`, {
    method: 'GET',
    params: { includeItems },
  });
}

export async function getCollectionItems(id: number, page = 1, size = 30) {
  return request<ApiResponse<PageResult<MediaItem>>>(`/api/v1/collections/${id}/items`, {
    method: 'GET',
    params: { page, size },
  });
}

export async function createCollection(data: CollectionPayload) {
  return request<ApiResponse<MediaCollection>>('/api/v1/collections', { method: 'POST', data });
}

export async function updateCollection(id: number, data: Partial<CollectionPayload>) {
  return request<ApiResponse<MediaCollection>>(`/api/v1/collections/${id}`, { method: 'PUT', data });
}

export async function deleteCollection(id: number) {
  return request<ApiResponse<void>>(`/api/v1/collections/${id}`, { method: 'DELETE' });
}

export async function addItemsToCollection(id: number, itemIds: number[]) {
  return request<ApiResponse<MediaCollection>>(`/api/v1/collections/${id}/items`, {
    method: 'POST',
    data: { itemIds },
    params: { includeItems: false },
  });
}

export async function removeItemFromCollection(id: number, mediaItemId: number) {
  return request<ApiResponse<MediaCollection>>(`/api/v1/collections/${id}/items/${mediaItemId}`, {
    method: 'DELETE',
    params: { includeItems: false },
  });
}
