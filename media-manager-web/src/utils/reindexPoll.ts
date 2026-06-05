import { getReindexStatus, startReindexSearch } from '@/services/search';

export type ReindexStatus = {
  state?: string;
  phase?: string;
  ftsIndexed?: number;
  embedIndexed?: number;
  message?: string;
};

export async function pollReindexUntilDone(
  onProgress?: (status: ReindexStatus) => void,
  intervalMs = 2000,
  maxAttempts = 300,
): Promise<ReindexStatus> {
  for (let i = 0; i < maxAttempts; i += 1) {
    const res = await getReindexStatus();
    const status: ReindexStatus = res?.data || {};
    onProgress?.(status);
    if (status.state === 'done' || status.state === 'failed') {
      return status;
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  throw new Error('索引重建超时，请稍后在系统设置查看状态');
}

export async function runReindexWithPolling(
  onProgress?: (status: ReindexStatus) => void,
): Promise<ReindexStatus> {
  await startReindexSearch();
  return pollReindexUntilDone(onProgress);
}
