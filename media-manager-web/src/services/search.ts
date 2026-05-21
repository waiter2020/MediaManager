import { request } from '@umijs/max';

export async function searchKeyword(q: string, params?: { libraryId?: number; page?: number; size?: number }) {
  return request('/api/v1/search', { method: 'GET', params: { q, ...params } });
}

export async function searchSemantic(data: { query: string; libraryId?: number; limit?: number }) {
  return request('/api/v1/search/semantic', { method: 'POST', data });
}

export async function searchQuery(data: { query: string; libraryId?: number; page?: number; size?: number }) {
  return request('/api/v1/search/query', { method: 'POST', data });
}

export async function reindexSearch() {
  return request('/api/v1/search/reindex', { method: 'POST' });
}
