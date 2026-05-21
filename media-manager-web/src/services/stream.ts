import { request } from '@umijs/max';

export async function getPlaybackInfo(fileId: number) {
  return request<{ mode: string; url: string }>(`/api/v1/stream/${fileId}/playback`, { method: 'GET' });
}

export function getFileStreamUrl(fileId: number) {
  const token = localStorage.getItem('accessToken') || '';
  return `/api/v1/stream/${fileId}?token=${token}`;
}

export function appendAuthToken(url: string) {
  const token = localStorage.getItem('accessToken') || '';
  if (!token || url.includes('token=')) {
    return url;
  }
  const sep = url.includes('?') ? '&' : '?';
  return `${url}${sep}token=${encodeURIComponent(token)}`;
}

export function getRawImageUrl(fileId: number) {
  const token = localStorage.getItem('accessToken') || '';
  return `/api/v1/stream/raw/${fileId}?token=${token}`;
}

export function getHlsMasterUrl(fileId: number) {
  const token = localStorage.getItem('accessToken') || '';
  return `/api/v1/stream/${fileId}/hls/master.m3u8?token=${encodeURIComponent(token)}`;
}
