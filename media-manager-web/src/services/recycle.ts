import { request } from '@umijs/max';

export async function listRecycleBin() {
  return request('/api/v1/recycle-bin', { method: 'GET' });
}

export async function restoreRecycleFile(fileId: number) {
  return request(`/api/v1/recycle-bin/${fileId}/restore`, { method: 'POST' });
}

export async function purgeRecycleFile(fileId: number) {
  return request(`/api/v1/recycle-bin/${fileId}`, { method: 'DELETE' });
}
