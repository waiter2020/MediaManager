import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';

export interface GeneralSettings {
  version: string;
  setupCompleted: boolean;
}

export interface SecuritySettings {
  authEnabled: boolean;
  effectiveAuthEnabled: boolean;
  requiresRestart: boolean;
  restartRequired: boolean;
}

export interface MediaProcessingSettings {
  ffmpegPath: string;
  ffprobePath: string;
}

export interface IntegrationsSettings {
  tmdbApiKey: string;
  tmdbApiKeyConfigured: boolean;
}

export interface AppearanceSettings {
  theme: 'dark' | 'light' | 'system';
}

export interface PublicSystemStatus {
  setupCompleted?: boolean;
  version?: string;
  theme?: string;
}

export async function getSettingsSummary() {
  return request<ApiResponse<GeneralSettings>>('/api/v1/system/settings/summary', {
    method: 'GET',
  });
}

export async function getSecuritySettings() {
  return request<ApiResponse<SecuritySettings>>('/api/v1/system/settings/security', {
    method: 'GET',
  });
}

export async function updateSecuritySettings(data: { authEnabled?: boolean }) {
  return request<ApiResponse<SecuritySettings>>('/api/v1/system/settings/security', {
    method: 'PUT',
    data,
  });
}

export async function getMediaProcessingSettings() {
  return request<ApiResponse<MediaProcessingSettings>>(
    '/api/v1/system/settings/media-processing',
    { method: 'GET' },
  );
}

export async function updateMediaProcessingSettings(data: {
  ffmpegPath?: string;
  ffprobePath?: string;
}) {
  return request<ApiResponse<MediaProcessingSettings>>(
    '/api/v1/system/settings/media-processing',
    { method: 'PUT', data },
  );
}

export async function getIntegrationsSettings() {
  return request<ApiResponse<IntegrationsSettings>>('/api/v1/system/settings/integrations', {
    method: 'GET',
  });
}

export async function updateIntegrationsSettings(data: { tmdbApiKey?: string }) {
  return request<ApiResponse<IntegrationsSettings>>('/api/v1/system/settings/integrations', {
    method: 'PUT',
    data,
  });
}

export async function getAppearanceSettings() {
  return request<ApiResponse<AppearanceSettings>>('/api/v1/system/settings/appearance', {
    method: 'GET',
  });
}

export async function updateAppearanceSettings(data: { theme?: string }) {
  return request<ApiResponse<AppearanceSettings>>('/api/v1/system/settings/appearance', {
    method: 'PUT',
    data,
  });
}

export async function getPublicSystemStatus() {
  return request<ApiResponse<PublicSystemStatus>>('/api/v1/system/status', { method: 'GET' });
}
