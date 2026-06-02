import { request } from '@umijs/max';
import type { ApiResponse, PageResult } from '@/types/api';
import type { MediaItem } from '@/types/media';

export interface GetItemsParams {
  libraryId?: number;
  type?: string;
  keyword?: string;
  categoryIds?: number[];
  tagIds?: number[];
  minYear?: number;
  maxYear?: number;
  minRating?: number;
  page?: number;
  size?: number;
  sortField?: string;
  sortOrder?: 'asc' | 'desc';
}

export interface MediaFilters {
  tags?: Array<{ id: number; name: string; color?: string }>;
  categories?: CategoryFilter[];
}

export interface CategoryFilter {
  id: number;
  name: string;
  type?: string;
  children?: CategoryFilter[];
}

export interface BatchClassifyResult {
  succeeded?: number;
  failed?: number;
}

export interface SeasonItem {
  id: number;
  seasonNumber?: number;
  name?: string;
  overview?: string;
  episodes?: Array<{
    id?: number;
    episodeNumber?: number;
    title?: string;
    overview?: string;
    runtimeMinutes?: number;
    airDate?: string;
    mediaFileId?: number;
    mediaItemId?: number;
  }>;
}

export interface SeasonSyncResult {
  seasonCount?: number;
}

export interface SimilarItemsResponse {
  items?: MediaItem[];
  scoredItems?: Array<{ item: MediaItem; score?: number }>;
  hint?: string;
}

export interface IdentifyCandidate {
  id?: string | number;
  externalId?: string | number;
  title?: string;
  releaseDate?: string;
  date?: string;
}

export type MetadataUpdatePayload = object;

export async function getItems(params: GetItemsParams) {
  return request<ApiResponse<PageResult<MediaItem>>>('/api/v1/items', { method: 'GET', params });
}

export async function getItem(id: number) {
  return request<ApiResponse<MediaItem>>(`/api/v1/items/${id}`, { method: 'GET' });
}

export async function getItemDetail(id: number) {
  return request<ApiResponse<MediaItem>>(`/api/v1/items/${id}/detail`, { method: 'GET' });
}

export async function getItemSeasons(id: number) {
  return request<ApiResponse<SeasonItem[]>>(`/api/v1/items/${id}/seasons`, { method: 'GET' });
}

export async function syncTvSeasons(id: number) {
  return request<ApiResponse<SeasonSyncResult>>(`/api/v1/items/${id}/seasons/sync`, { method: 'POST' });
}

export async function getSimilarItems(id: number, limit = 12) {
  return request<ApiResponse<SimilarItemsResponse | MediaItem[]>>(`/api/v1/items/${id}/similar`, {
    method: 'GET',
    params: { limit },
  });
}

export async function updateMetadata(id: number, data: MetadataUpdatePayload) {
  return request<ApiResponse<void>>(`/api/v1/items/${id}/metadata`, { method: 'PUT', data });
}

export async function refreshMetadata(id: number) {
  return request<ApiResponse<void>>(`/api/v1/items/${id}/refresh`, { method: 'POST' });
}

export async function classifyItem(id: number) {
  return request<ApiResponse<void>>(`/api/v1/items/${id}/classify`, { method: 'POST' });
}

export async function classifyBatch(itemIds: number[]) {
  return request<ApiResponse<BatchClassifyResult>>('/api/v1/items/classify-batch', {
    method: 'POST',
    data: { itemIds },
  });
}

export async function identifyItem(id: number, data: { provider?: string; externalId: string }) {
  return request<ApiResponse<void>>(`/api/v1/items/${id}/identify`, { method: 'POST', data });
}

export async function searchTmdbCandidates(id: number, q: string) {
  return request<ApiResponse<IdentifyCandidate[]>>(`/api/v1/items/${id}/tmdb/search`, {
    method: 'GET',
    params: { q },
  });
}

export async function searchJavBusCandidates(id: number, q: string) {
  return request<ApiResponse<IdentifyCandidate[]>>(`/api/v1/items/${id}/javbus/search`, {
    method: 'GET',
    params: { q },
  });
}

export async function searchStashDbCandidates(id: number, q: string) {
  return request<ApiResponse<IdentifyCandidate[]>>(`/api/v1/items/${id}/stashdb/search`, {
    method: 'GET',
    params: { q },
  });
}

export async function deleteItem(id: number) {
  return request<ApiResponse<void>>(`/api/v1/items/${id}`, { method: 'DELETE' });
}

export async function deleteSourceFile(id: number) {
  return request<ApiResponse<void>>(`/api/v1/items/${id}/file`, { method: 'DELETE' });
}

export async function getFilters() {
  return request<ApiResponse<MediaFilters>>('/api/v1/items/filters', { method: 'GET' });
}
