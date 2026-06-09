import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';
import type {
  LibraryPath,
  LibraryPluginConfig,
  LibraryScanRequest,
  LibraryType,
  MediaLibrary,
} from '@/types/library';

export interface LibraryUpsertPayload {
  name: string;
  type: LibraryType;
  language?: string;
  autoScan?: boolean;
  scanIntervalMinutes?: number;
  paths?: LibraryPath[];
  plugins?: LibraryPluginConfig[];
}

export interface LibraryStats {
  totalItems?: number;
  videoCount?: number;
  imageCount?: number;
  audioCount?: number;
  lastScannedAt?: string;
}



export async function getLibraries() {
  return request<ApiResponse<MediaLibrary[]>>('/api/v1/libraries', { method: 'GET' });
}

export async function getLibrary(id: number) {
  return request<ApiResponse<MediaLibrary>>(`/api/v1/libraries/${id}`, { method: 'GET' });
}

export async function createLibrary(data: LibraryUpsertPayload) {
  return request<ApiResponse<MediaLibrary>>('/api/v1/libraries', { method: 'POST', data });
}

export async function deleteLibrary(id: number) {
  return request<ApiResponse<void>>(`/api/v1/libraries/${id}`, { method: 'DELETE' });
}

export async function triggerScan(id: number, options?: LibraryScanRequest) {
  return request<ApiResponse<void>>(`/api/v1/libraries/${id}/scan`, { method: 'POST', data: options });
}

export async function cancelScan(id: number) {
  return request<ApiResponse<void>>(`/api/v1/system/scan/${id}/cancel`, { method: 'POST' });
}

export async function updateLibrary(id: number, data: Partial<LibraryUpsertPayload>) {
  return request<ApiResponse<MediaLibrary>>(`/api/v1/libraries/${id}`, { method: 'PUT', data });
}

export async function getLibraryStats(id: number) {
  return request<ApiResponse<LibraryStats>>(`/api/v1/libraries/${id}/stats`, { method: 'GET' });
}
