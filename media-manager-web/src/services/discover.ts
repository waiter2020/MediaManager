import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';
import type { MediaItem } from '@/types/media';

export interface DiscoverResponse {
  continueWatching?: MediaItem[];
  watchlist?: MediaItem[];
  recommended?: MediaItem[];
  favorites?: MediaItem[];
  topRated?: MediaItem[];
  unwatched?: MediaItem[];
  recentlyAdded?: MediaItem[];
}

export async function getDiscover(limit = 20) {
  return request<ApiResponse<DiscoverResponse>>('/api/v1/discover', {
    method: 'GET',
    params: { limit },
  });
}
