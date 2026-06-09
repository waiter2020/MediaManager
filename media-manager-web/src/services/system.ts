import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';
import type { LoginRequest, LoginResponse } from '@/services/auth';
import type { ScanProgress } from '@/models/global';

export interface SystemStatus {
  setupCompleted?: boolean;
  version?: string;
  theme?: string;
}

export interface SystemInfo extends SystemStatus {
  javaVersion?: string;
  totalUsers?: number;
  totalLibraries?: number;
  totalMediaItems?: number;
  videoCount?: number;
  imageCount?: number;
  audioCount?: number;
  tagCount?: number;
  hasViewableLibraries?: boolean;
  [key: string]: unknown;
}

export interface DirectoryItem {
  name: string;
  path: string;
  hasChildren?: boolean;
}

export interface SystemConfigItem {
  key: string;
  value: string;
  description?: string;
}

export interface SystemCapabilities {
  ffmpegAvailable?: boolean;
  ffmpegPath?: string;
  ffprobeAvailable?: boolean;
  ffprobePath?: string;
  embeddingCount?: number;
  hasIndexedVectors?: boolean;
  embeddingAvailable?: boolean;
  aiProvider?: string;
  aiProviderName?: string;
  llmProvider?: string;
  llmProviderName?: string;
  embedProvider?: string;
  embedProviderName?: string;
  embedModel?: string;
  llmModel?: string;
  aiBaseUrl?: string;
  llmBaseUrl?: string;
  embedBaseUrl?: string;
  classifierEnabled?: boolean;
  isNoopProvider?: boolean;
  aiDegraded?: boolean;
  aiDegradedReason?: string;
  hardwareAccelerationConfigured?: string;
  hardwareAccelerationResolved?: string;
  hardwareEncoderAvailable?: boolean;
  hardwareAccelerationWarnings?: string[];
  hardwareEncodersAvailable?: Record<string, boolean>;
}

export interface SystemLogEvent {
  timestamp: number;
  level: string;
  source?: string;
  logger?: string;
  message: string;
}

export async function checkSetup() {
  return request<ApiResponse<SystemStatus>>('/api/v1/system/status', { method: 'GET' });
}

export async function setupSystem(data: LoginRequest) {
  return request<ApiResponse<void>>('/api/v1/auth/setup', { method: 'POST', data });
}

export async function login(data: LoginRequest) {
  return request<ApiResponse<LoginResponse>>('/api/v1/auth/login', { method: 'POST', data });
}

export async function getDashboardStats() {
  return request<ApiResponse<SystemStatus>>('/api/v1/system/status', { method: 'GET' });
}

export async function getSystemInfo() {
  return request<ApiResponse<SystemInfo>>('/api/v1/system/info', { method: 'GET' });
}

export async function getSystemCapabilities() {
  return request<ApiResponse<SystemCapabilities>>('/api/v1/system/capabilities', { method: 'GET' });
}

export async function getScanStatus() {
  return request<ApiResponse<ScanProgress[]>>('/api/v1/system/scan/status', { method: 'GET' });
}

export async function getDirectories(path?: string) {
  return request<ApiResponse<DirectoryItem[]>>('/api/v1/system/directories', {
    method: 'GET',
    params: { path },
  });
}


export async function getSystemConfig() {
  return request<ApiResponse<SystemConfigItem[]>>('/api/v1/system/config', { method: 'GET' });
}

export async function putSystemConfig(data: Record<string, string>) {
  return request<ApiResponse<void>>('/api/v1/system/config', { method: 'PUT', data });
}

export async function getLogsRecent(limit = 100) {
  return request<ApiResponse<SystemLogEvent[]>>('/api/v1/system/logs/recent', {
    method: 'GET',
    params: { limit },
  });
}
