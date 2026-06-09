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

export type HardwareAccelerationType =
  | 'none'
  | 'auto'
  | 'nvenc'
  | 'qsv'
  | 'vaapi'
  | 'amf';

export interface HardwareAccelerationProbe {
  configuredType?: string;
  resolvedType?: string;
  resolvedEncoder?: string;
  devicePath?: string;
  encodersAvailable?: Record<string, boolean>;
  warnings?: string[];
}

export interface MediaProcessingSettings {
  ffmpegPath: string;
  ffprobePath: string;
  hardwareAcceleration?: HardwareAccelerationType | string;
  hardwareDevice?: string;
  hardwareEncoder?: string;
  hardwareProbe?: HardwareAccelerationProbe;
}

export interface IntegrationsSettings {
  tmdbApiKey: string;
  tmdbApiKeyConfigured: boolean;
  opensubtitlesApiKey?: string;
  opensubtitlesApiKeyConfigured?: boolean;
  opensubtitlesUsername?: string;
  opensubtitlesUsernameConfigured?: boolean;
  opensubtitlesPassword?: string;
  opensubtitlesPasswordConfigured?: boolean;
  subtitleDefaultLanguage?: string;
}

export interface SubtitleProviderStatus {
  id: string;
  configured: boolean;
  enabled: boolean;
}

export interface SubtitleSettings {
  defaultLanguage: string;
  providers: SubtitleProviderStatus[];
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
  hardwareAcceleration?: string;
  hardwareDevice?: string;
  hardwareEncoder?: string;
}) {
  return request<ApiResponse<MediaProcessingSettings>>(
    '/api/v1/system/settings/media-processing',
    { method: 'PUT', data },
  );
}

export async function probeHardwareAcceleration() {
  return request<ApiResponse<HardwareAccelerationProbe>>(
    '/api/v1/system/hardware-acceleration/probe',
    { method: 'GET' },
  );
}

export async function getIntegrationsSettings() {
  return request<ApiResponse<IntegrationsSettings>>('/api/v1/system/settings/integrations', {
    method: 'GET',
  });
}

export async function updateIntegrationsSettings(data: {
  tmdbApiKey?: string;
  opensubtitlesApiKey?: string;
  opensubtitlesUsername?: string;
  opensubtitlesPassword?: string;
  subtitleDefaultLanguage?: string;
}) {
  return request<ApiResponse<IntegrationsSettings>>('/api/v1/system/settings/integrations', {
    method: 'PUT',
    data,
  });
}

export async function getSubtitleSettings() {
  return request<ApiResponse<SubtitleSettings>>('/api/v1/system/settings/subtitles', {
    method: 'GET',
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
