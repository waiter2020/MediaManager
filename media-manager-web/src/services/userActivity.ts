import { request } from '@umijs/max';

export async function getRecentPlayed(params?: { limit?: number }) {
  return request('/api/v1/user/recent/played', { method: 'GET', params });
}

export async function getRecentFavorites(params?: { limit?: number }) {
  return request('/api/v1/user/recent/favorites', { method: 'GET', params });
}

export async function recordPlay(data: { mediaItemId: number; position?: number }) {
  return request('/api/v1/user/play', { method: 'POST', data });
}

export async function toggleFavorite(mediaItemId: number) {
  return request(`/api/v1/user/favorite/${mediaItemId}`, { method: 'POST' });
}

export async function checkFavorite(mediaItemId: number) {
  return request(`/api/v1/user/favorite/${mediaItemId}`, { method: 'GET' });
}
