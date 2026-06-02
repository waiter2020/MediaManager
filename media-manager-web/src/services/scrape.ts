import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';

export interface ScrapeScheduleDto {
  id?: number;
  name: string;
  enabled: boolean;
  scheduleType: 'CRON' | 'FIXED_DELAY';
  cronExpr?: string;
  intervalSeconds?: number;
  scope: 'GLOBAL' | 'LIBRARY';
  libraryId?: number;
  targetStatus: 'UNIDENTIFIED' | 'IDENTIFIED' | 'ALL';
  mediaTypes?: string;
  maxConcurrency: number;
  batchSizeOverride?: number;
  requestDelayMsOverride?: number;
  nextRunAt?: string;
  lastRunAt?: string;
  lastStatus?: string;
  lastError?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ScrapeTaskResponse {
  id: number;
  scheduleId?: number;
  libraryId?: number;
  libraryName?: string;
  status: string;
  triggerType: string;
  targetStatus?: string;
  mediaTypes?: string;
  totalItems: number;
  scrapedItems: number;
  errorItems: number;
  errorLog?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
}

export async function listScrapeSchedules() {
  return request<ApiResponse<ScrapeScheduleDto[]>>('/api/v1/scrape/schedules', { method: 'GET' });
}

export async function getScrapeSchedule(id: number) {
  return request<ApiResponse<ScrapeScheduleDto>>(`/api/v1/scrape/schedules/${id}`, { method: 'GET' });
}

export async function createScrapeSchedule(data: ScrapeScheduleDto) {
  return request<ApiResponse<ScrapeScheduleDto>>('/api/v1/scrape/schedules', { method: 'POST', data });
}

export async function updateScrapeSchedule(id: number, data: ScrapeScheduleDto) {
  return request<ApiResponse<ScrapeScheduleDto>>(`/api/v1/scrape/schedules/${id}`, {
    method: 'PUT',
    data,
  });
}

export async function deleteScrapeSchedule(id: number) {
  return request<ApiResponse<void>>(`/api/v1/scrape/schedules/${id}`, { method: 'DELETE' });
}

export async function runOnceScrapeSchedule(id: number) {
  return request<ApiResponse<ScrapeTaskResponse>>(`/api/v1/scrape/schedules/${id}/runOnce`, {
    method: 'POST',
  });
}

export async function listScrapeTasks(params?: { scheduleId?: number }) {
  return request<ApiResponse<ScrapeTaskResponse[]>>('/api/v1/scrape/tasks', {
    method: 'GET',
    params,
  });
}

export async function createScrapeTask(data?: {
  libraryId?: number;
  targetStatus?: 'UNIDENTIFIED' | 'IDENTIFIED' | 'ALL';
}) {
  return request<ApiResponse<ScrapeTaskResponse>>('/api/v1/scrape/tasks', {
    method: 'POST',
    data: data ?? {},
  });
}

export async function listScrapeTasksBySchedule(scheduleId: number) {
  return listScrapeTasks({ scheduleId });
}

export async function cancelScrapeTask(taskId: number) {
  return request<ApiResponse<void>>(`/api/v1/scrape/tasks/${taskId}/cancel`, { method: 'POST' });
}

export async function getScrapeTask(taskId: number) {
  return request<ApiResponse<ScrapeTaskResponse>>(`/api/v1/scrape/tasks/${taskId}`, {
    method: 'GET',
  });
}

export async function startScrapeAll(targetStatus?: string) {
  return request<ApiResponse<ScrapeTaskResponse | null>>('/api/v1/scrape/start', {
    method: 'POST',
    data: targetStatus ? { targetStatus } : {},
  });
}

export async function startScrapeLibrary(libraryId: number, targetStatus?: string) {
  return request<ApiResponse<ScrapeTaskResponse | null>>(`/api/v1/scrape/start/${libraryId}`, {
    method: 'POST',
    data: targetStatus ? { targetStatus } : {},
  });
}
