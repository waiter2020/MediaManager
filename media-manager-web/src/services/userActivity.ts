import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';
import type { MediaItem } from '@/types/media';

export async function getRecentPlayed(params?: { limit?: number }) {
  return request<ApiResponse<MediaItem[]>>('/api/v1/user/recent/played', { method: 'GET', params });
}

export async function getRecentFavorites(params?: { limit?: number }) {
  return request<ApiResponse<MediaItem[]>>('/api/v1/user/recent/favorites', {
    method: 'GET',
    params,
  });
}

export async function recordPlay(data: { mediaItemId: number; position?: number }) {
  return request<ApiResponse<void>>('/api/v1/user/play', { method: 'POST', data });
}

export async function toggleFavorite(mediaItemId: number) {
  return request<ApiResponse<{ favorite?: boolean }>>(`/api/v1/user/favorite/${mediaItemId}`, {
    method: 'POST',
  });
}

export async function checkFavorite(mediaItemId: number) {
  return request<ApiResponse<{ favorite?: boolean }>>(`/api/v1/user/favorite/${mediaItemId}`, {
    method: 'GET',
  });
}
