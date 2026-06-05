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

export async function getContinueWatching(params?: { limit?: number }) {
  return request<ApiResponse<MediaItem[]>>('/api/v1/user/continue-watching', {
    method: 'GET',
    params,
  });
}

export async function getWatchlist(params?: { limit?: number }) {
  return request<ApiResponse<MediaItem[]>>('/api/v1/user/watchlist', {
    method: 'GET',
    params,
  });
}

export interface PlaybackStats {
  playedItemCount?: number;
  completedItemCount?: number;
  totalPlaybackSeconds?: number;
  favoriteCount?: number;
  watchlistCount?: number;
  recentPlayed?: MediaItem[];
  mostPlayed?: MediaItem[];
  watchlist?: MediaItem[];
}

export async function getPlaybackStats(params?: { limit?: number }) {
  return request<ApiResponse<PlaybackStats>>('/api/v1/user/playback/stats', {
    method: 'GET',
    params,
  });
}

export async function recordPlay(data: {
  mediaItemId: number;
  position?: number;
  durationSeconds?: number;
  completed?: boolean;
}) {
  return request<ApiResponse<void>>('/api/v1/user/play', { method: 'POST', data });
}

export async function toggleFavorite(mediaItemId: number) {
  return request<ApiResponse<{ favorite?: boolean; favorited?: boolean }>>(`/api/v1/user/favorite/${mediaItemId}`, {
    method: 'POST',
  });
}

export async function checkFavorite(mediaItemId: number) {
  return request<ApiResponse<{ favorite?: boolean; favorited?: boolean }>>(`/api/v1/user/favorite/${mediaItemId}`, {
    method: 'GET',
  });
}

export async function toggleWatchlist(mediaItemId: number) {
  return request<ApiResponse<{ watchlist?: boolean; watchlisted?: boolean }>>(`/api/v1/user/watchlist/${mediaItemId}`, {
    method: 'POST',
  });
}

export async function checkWatchlist(mediaItemId: number) {
  return request<ApiResponse<{ watchlist?: boolean; watchlisted?: boolean }>>(`/api/v1/user/watchlist/${mediaItemId}`, {
    method: 'GET',
  });
}

export async function setWatched(mediaItemId: number, watched: boolean) {
  return request<ApiResponse<{ watched: boolean }>>(`/api/v1/user/watched/${mediaItemId}`, {
    method: 'POST',
    data: { watched },
  });
}

export async function checkWatched(mediaItemId: number) {
  return request<ApiResponse<{ watched: boolean }>>(`/api/v1/user/watched/${mediaItemId}`, {
    method: 'GET',
  });
}
