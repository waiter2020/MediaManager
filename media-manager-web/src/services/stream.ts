import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';
import { getAccessToken } from '@/utils/authSession';

export async function getPlaybackInfo(fileId: number) {
  return request<ApiResponse<{ mode: string; url: string }>>(`/api/v1/stream/${fileId}/playback`, {
    method: 'GET',
  });
}

export function getFileStreamUrl(fileId: number) {
  const token = getAccessToken() || '';
  return `/api/v1/stream/${fileId}?token=${token}`;
}

export function appendAuthToken(url: string) {
  const token = getAccessToken() || '';
  if (!token || url.includes('token=')) {
    return url;
  }
  const sep = url.includes('?') ? '&' : '?';
  return `${url}${sep}token=${encodeURIComponent(token)}`;
}

export function getRawImageUrl(fileId: number) {
  const token = getAccessToken() || '';
  return `/api/v1/stream/raw/${fileId}?token=${token}`;
}

export function getHlsMasterUrl(fileId: number) {
  const token = getAccessToken() || '';
  return `/api/v1/stream/${fileId}/hls/master.m3u8?token=${encodeURIComponent(token)}`;
}

/** Contract-aligned image URL (alias of stream/images). */
export function getStreamImageUrl(fileId: number, width?: number) {
  const token = getAccessToken() || '';
  const w = width ? `&w=${width}` : '';
  return `/api/v1/images/${fileId}?token=${encodeURIComponent(token)}${w}`;
}

export function getItemPosterUrl(itemId: number) {
  const token = getAccessToken() || '';
  return `/api/v1/items/${itemId}/poster?token=${encodeURIComponent(token)}`;
}

export function getItemBackdropUrl(itemId: number) {
  const token = getAccessToken() || '';
  return `/api/v1/items/${itemId}/backdrop?token=${encodeURIComponent(token)}`;
}

/** posterPath/backdropPath from scrapers may be remote URLs (e.g. TMDB) or local files. */
export function isRemoteMediaPath(path?: string | null): boolean {
  if (!path) return false;
  const trimmed = path.trim();
  return trimmed.startsWith('http://') || trimmed.startsWith('https://');
}

export function resolveItemPosterUrl(options: {
  itemId: number;
  posterPath?: string | null;
  type?: string;
  fileIds?: number[];
  thumbnailWidth?: number;
}): string | null {
  const { itemId, posterPath, type, fileIds, thumbnailWidth = 300 } = options;
  if (posterPath) {
    return isRemoteMediaPath(posterPath) ? posterPath : getItemPosterUrl(itemId);
  }
  if (type === 'IMAGE' && fileIds && fileIds.length > 0) {
    return getStreamImageUrl(fileIds[0], thumbnailWidth);
  }
  return null;
}

export function resolveItemBackdropUrl(options: {
  itemId: number;
  backdropPath?: string | null;
  posterPath?: string | null;
  type?: string;
  fileIds?: number[];
  thumbnailWidth?: number;
}): string | null {
  const { itemId, backdropPath, posterPath, type, fileIds, thumbnailWidth = 400 } = options;
  if (backdropPath) {
    return isRemoteMediaPath(backdropPath) ? backdropPath : getItemBackdropUrl(itemId);
  }
  return resolveItemPosterUrl({ itemId, posterPath, type, fileIds, thumbnailWidth });
}

export function getItemPreviewUrl(itemId: number) {
  const token = getAccessToken() || '';
  return `/api/v1/items/${itemId}/preview?token=${encodeURIComponent(token)}`;
}

export async function refreshStreamToken() {
  return request<ApiResponse<void>>('/api/v1/stream/token', {
    method: 'POST',
  });
}

