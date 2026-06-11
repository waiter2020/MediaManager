import { request } from '@umijs/max';
import type { ApiResponse, PageResult } from '@/types/api';
import type { MediaItem } from '@/types/media';

export interface SearchParams {
  libraryId?: number;
  page?: number;
  size?: number;
}

export interface SemanticSearchResponse {
  items?: MediaItem[];
  scoredItems?: Array<{ item: MediaItem; score: number }>;
  hint?: string;
}

export interface NaturalLanguageSearchResponse {
  results?: PageResult<MediaItem>;
  parsedFilters?: ParsedSearchFilters;
}

export interface UnifiedSearchRequest {
  query: string;
  libraryId?: number;
  type?: string;
  categoryIds?: number[];
  tagIds?: number[];
  minYear?: number;
  maxYear?: number;
  minRating?: number;
  hasSubtitle?: boolean;
  page?: number;
  size?: number;
}

export interface UnifiedSearchResponse {
  results?: PageResult<MediaItem>;
  parsedFilters?: ParsedSearchFilters;
  sources?: string[];
  hint?: string;
}

export type ParsedSearchFilterValue = string | number | boolean | string[] | number[] | null;
export type ParsedSearchFilters = Record<string, ParsedSearchFilterValue>;

export interface ReindexStatus {
  state?: string;
  message?: string;
  processed?: number;
  total?: number;
}

export async function searchKeyword(q: string, params?: SearchParams) {
  return request<ApiResponse<PageResult<MediaItem>>>('/api/v1/search', {
    method: 'GET',
    params: { q, ...params },
  });
}

export async function searchSemantic(data: { query: string; libraryId?: number; limit?: number; page?: number; size?: number }) {
  return request<ApiResponse<SemanticSearchResponse | MediaItem[] | PageResult<MediaItem>>>('/api/v1/search/semantic', {
    method: 'POST',
    data,
  });
}

export async function searchQuery(data: { query: string; libraryId?: number; page?: number; size?: number }) {
  return request<ApiResponse<NaturalLanguageSearchResponse | PageResult<MediaItem>>>(
    '/api/v1/search/query',
    { method: 'POST', data },
  );
}

export async function searchUnified(data: UnifiedSearchRequest) {
  return request<ApiResponse<UnifiedSearchResponse>>('/api/v1/search/unified', {
    method: 'POST',
    data,
  });
}

export async function startReindexSearch() {
  return request<ApiResponse<ReindexStatus>>('/api/v1/search/reindex', {
    method: 'POST',
    timeout: 60000,
  });
}

/** @deprecated use startReindexSearch + getReindexStatus polling */
export async function reindexSearch() {
  return startReindexSearch();
}

export async function getReindexStatus() {
  return request<ApiResponse<ReindexStatus>>('/api/v1/search/reindex/status', { method: 'GET' });
}
