import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';

export interface RecycleBinItem {
  id: number;
  mediaItemId?: number;
  mediaTitle?: string;
  libraryName?: string;
  fileName?: string;
  filePath?: string;
  fileSize?: number;
  deletedAt?: string;
}

export async function listRecycleBin() {
  return request<ApiResponse<RecycleBinItem[]>>('/api/v1/recycle-bin', { method: 'GET' });
}

export async function restoreRecycleFile(fileId: number) {
  return request<ApiResponse<void>>(`/api/v1/recycle-bin/${fileId}/restore`, { method: 'POST' });
}

export async function purgeRecycleFile(fileId: number, opts?: { deleteSource?: boolean }) {
  const params = opts?.deleteSource ? { deleteSource: true } : undefined;
  return request<ApiResponse<void>>(`/api/v1/recycle-bin/${fileId}`, { method: 'DELETE', params });
}
