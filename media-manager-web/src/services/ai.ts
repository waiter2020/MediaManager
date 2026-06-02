import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';

export interface AiConfigPayload {
  defaultProvider: string;
  ollamaBaseUrl: string;
  openaiBaseUrl: string;
  openaiApiKey?: string;
  llmModel: string;
  embedModel: string;
  classifierEnabled: boolean;
  outboundAllowed: boolean;
  timeoutMs: number;
  autoApproveEnabled?: boolean;
  autoApproveConfidenceThreshold?: number;
  autoApproveFields?: string;
}

export interface AiProviderDescriptor {
  id: string;
  displayName: string;
  kind: string;
  local?: boolean;
  configSchema?: {
    fields: { key: string; label: string; placeholder?: string }[];
  };
}

export interface AiHealth {
  status?: string;
  enabled?: boolean;
  provider?: string;
  displayName?: string;
  available?: boolean;
  message?: string;
  embedModel?: string;
  llmModel?: string;
  embeddingDimensions?: number;
  [key: string]: unknown;
}

export interface AiSuggestion {
  id: number;
  mediaItemId: number;
  mediaTitle?: string;
  fieldName: string;
  suggestedValue?: string;
  confidence?: number;
  providerId?: string;
  rawPayload?: unknown;
}

export interface BatchSuggestionResult {
  approved?: number;
  rejected?: number;
}

export async function getAiHealth(options?: { refresh?: boolean }) {
  return request<ApiResponse<AiHealth>>('/api/v1/ai/health', {
    method: 'GET',
    params: options?.refresh ? { refresh: true } : undefined,
  });
}

export function formatEmbeddingDimensions(dimensions?: number) {
  return typeof dimensions === 'number' && dimensions > 0 ? String(dimensions) : '未检测到';
}

export async function listAiProviders() {
  return request<ApiResponse<AiProviderDescriptor[]>>('/api/v1/ai/providers', {
    method: 'GET',
  });
}

export async function getAiConfig() {
  return request<ApiResponse<AiConfigPayload>>('/api/v1/ai/config', { method: 'GET' });
}

export async function updateAiConfig(data: AiConfigPayload) {
  return request<ApiResponse<AiConfigPayload>>('/api/v1/ai/config', {
    method: 'PUT',
    data,
  });
}

export async function listAiSuggestions() {
  return request<ApiResponse<AiSuggestion[]>>('/api/v1/ai/suggestions', { method: 'GET' });
}

export async function approveSuggestion(id: number) {
  return request<ApiResponse<void>>(`/api/v1/ai/suggestions/${id}/approve`, { method: 'POST' });
}

export async function rejectSuggestion(id: number) {
  return request<ApiResponse<void>>(`/api/v1/ai/suggestions/${id}/reject`, { method: 'POST' });
}

export async function batchApproveSuggestions(ids: number[]) {
  return request<ApiResponse<BatchSuggestionResult>>('/api/v1/ai/suggestions/batch-approve', {
    method: 'POST',
    data: { ids },
  });
}

export async function batchRejectSuggestions(ids: number[]) {
  return request<ApiResponse<BatchSuggestionResult>>('/api/v1/ai/suggestions/batch-reject', {
    method: 'POST',
    data: { ids },
  });
}

export function defaultLibraryAiConfig(providerId: string): string {
  if (providerId === 'openai-compatible') {
    return JSON.stringify(
      {
        baseUrl: 'https://api.openai.com/v1',
        apiKey: '',
        llmModel: 'gpt-4o-mini',
        embedModel: 'text-embedding-3-small',
      },
      null,
      2,
    );
  }
  return JSON.stringify(
    {
      baseUrl: 'http://localhost:11434',
      llmModel: 'qwen2.5:7b',
      embedModel: 'nomic-embed-text',
    },
    null,
    2,
  );
}
