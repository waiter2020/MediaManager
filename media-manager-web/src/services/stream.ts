import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';
import { getAccessToken } from '@/utils/authSession';

export type PlaybackModePreference = 'auto' | 'direct' | 'hls';
export type PlaybackMode = 'direct' | 'hls';
export type PlaybackQuality = 'auto' | 'source' | '2160p' | '1080p' | '720p' | '480p' | '360p';
export type TranscodeMode = 'auto' | 'software' | 'hardware';
export type PlaybackPurpose = 'playback' | 'preview';

export interface PlaybackOption {
  value: string;
  label: string;
  height?: number;
  bitrateKbps?: number;
}

export interface PlaybackInfo {
  mode: PlaybackMode;
  playMethod?: 'DirectPlay' | 'DirectStream' | 'Transcode' | string;
  url: string;
  variant?: string;
  quality: PlaybackQuality | string;
  transcodeMode: TranscodeMode | string;
  directPlayable: boolean;
  transcoding: boolean;
  container?: string;
  videoCodec?: string;
  audioCodec?: string;
  width?: number;
  height?: number;
  bitrate?: number;
  startOffset?: number;
  durationSeconds?: number;
  qualities?: PlaybackOption[];
  transcodeModes?: PlaybackOption[];
  transcodingReasons?: string[];
}

export interface TranscodeTelemetry {
  speed: number;
  fps: number;
  time: string;
  status: string;
}

export async function getPlaybackInfo(
  fileId: number,
  params?: {
    mode?: PlaybackModePreference;
    quality?: PlaybackQuality | string;
    transcodeMode?: TranscodeMode | string;
    start?: number;
    purpose?: PlaybackPurpose;
    kickoff?: boolean;
  },
) {
  return request<ApiResponse<PlaybackInfo>>(`/api/v1/stream/${fileId}/playback`, {
    method: 'GET',
    params,
    timeout: 120000,
  });
}

export async function getPreviewPlaybackInfo(
  fileId: number,
  options?: { start?: number; kickoff?: boolean },
) {
  const start = options?.start;
  return getPlaybackInfo(fileId, {
    mode: 'auto',
    purpose: 'preview',
    start: start != null && start > 0 ? start : undefined,
    kickoff: options?.kickoff,
  });
}

export async function stopTranscode(fileId: number) {
  return request<ApiResponse<void>>(`/api/v1/stream/${fileId}/transcode/stop`, {
    method: 'POST',
  });
}

export async function getTranscodeSpeed(fileId: number, variant?: string) {
  return request<ApiResponse<TranscodeTelemetry>>(`/api/v1/stream/${fileId}/transcode-speed`, {
    method: 'GET',
    params: variant ? { variant } : undefined,
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

export function getSubtitleTrackUrl(
  subtitleId: number,
  options?: { offset?: number; delay?: number },
) {
  const token = getAccessToken() || '';
  const params = new URLSearchParams();
  params.set('token', token);
  if (options?.offset != null && options.offset > 0) {
    params.set('offset', String(options.offset));
  }
  if (options?.delay != null && Math.abs(options.delay) > 0.001) {
    params.set('delay', String(options.delay));
  }
  return `/api/v1/stream/subtitles/${subtitleId}.vtt?${params.toString()}`;
}

export function getChapterThumbnailUrl(chapterId: number) {
  const token = getAccessToken() || '';
  return `/api/v1/items/chapters/${chapterId}/thumbnail?token=${encodeURIComponent(token)}`;
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

export async function refreshStreamToken() {
  return request<ApiResponse<void>>('/api/v1/stream/token', {
    method: 'POST',
  });
}
