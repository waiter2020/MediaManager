import { request } from '@umijs/max';
import type { ApiResponse, PageResult } from '@/types/api';

export interface AiConfigPayload {
  defaultProvider: string;
  llmProvider?: string;
  embedProvider?: string;
  ollamaBaseUrl: string;
  openaiBaseUrl: string;
  openaiApiKey?: string;
  openaiLlmBaseUrl?: string;
  openaiLlmApiKey?: string;
  openaiEmbedBaseUrl?: string;
  openaiEmbedApiKey?: string;
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
  llmProvider?: string;
  llmProviderName?: string;
  embedProvider?: string;
  embedProviderName?: string;
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

export interface AiOrganizationRequest {
  libraryId?: number;
  mergeDuplicateTags?: boolean;
  deleteUnusedTags?: boolean;
  deleteLowUsageTags?: boolean;
  protectManualTags?: boolean;
  recolorTags?: boolean;
  recolorManualTags?: boolean;
  createSmartCollections?: boolean;
  lowUsageThreshold?: number;
  maxCollections?: number;
  minCollectionTagUsage?: number;
  collectionItemLimit?: number;
}

export interface AiOrganizationTagUsage {
  id: number;
  name: string;
  color?: string;
  source?: string;
  usageCount?: number;
  cleanupReason?: string;
}

export interface AiOrganizationSmartCollectionCandidate {
  key?: string;
  dimension?: string;
  dimensionLabel?: string;
  name: string;
  value?: string;
  displayValue?: string;
  color?: string;
  source?: string;
  usageCount?: number;
  tagId?: number;
  tagName?: string;
  categoryId?: number;
  categoryName?: string;
  metadataField?: string;
  metadataValue?: string;
}

export interface AiOrganizationDuplicateGroup {
  semanticKey: string;
  canonicalTag: AiOrganizationTagUsage;
  duplicateTags: AiOrganizationTagUsage[];
}

export interface AiOrganizationGeneratedCollection {
  id?: number;
  name: string;
  dimension?: string;
  dimensionLabel?: string;
  value?: string;
  displayValue?: string;
  tagId?: number;
  tagName?: string;
  categoryId?: number;
  categoryName?: string;
  metadataField?: string;
  metadataValue?: string;
  itemCount?: number;
  created?: boolean;
}

export interface AiOrganizationResponse {
  libraryId?: number;
  applied?: boolean;
  unusedTagCount?: number;
  cleanupTagCount?: number;
  duplicateGroupCount?: number;
  smartCollectionCandidateCount?: number;
  deletedUnusedTagCount?: number;
  deletedCleanupTagCount?: number;
  mergedTagCount?: number;
  translatedTagCount?: number;
  recoloredTagCount?: number;
  createdCollectionCount?: number;
  unusedTags?: AiOrganizationTagUsage[];
  cleanupTags?: AiOrganizationTagUsage[];
  duplicateTagGroups?: AiOrganizationDuplicateGroup[];
  smartCollectionCandidates?: AiOrganizationSmartCollectionCandidate[];
  generatedCollections?: AiOrganizationGeneratedCollection[];
}

export interface AiOrganizationJobStatus {
  state?: 'idle' | 'queued' | 'running' | 'done' | 'failed' | 'cancelled';
  phase?: string;
  libraryId?: number;
  total?: number;
  processed?: number;
  failed?: number;
  mergedTagCount?: number;
  translatedTagCount?: number;
  deletedCleanupTagCount?: number;
  deletedUnusedTagCount?: number;
  recoloredTagCount?: number;
  createdCollectionCount?: number;
  cancelRequested?: boolean;
  startedAt?: number;
  finishedAt?: number;
  message?: string;
  result?: AiOrganizationResponse;
}

export interface AiOrganizationStartResult {
  accepted?: boolean;
  message?: string;
  status?: AiOrganizationJobStatus;
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

export async function listAiSuggestions(params?: { page?: number; size?: number }) {
  return request<ApiResponse<PageResult<AiSuggestion>>>('/api/v1/ai/suggestions', {
    method: 'GET',
    params,
  });
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

export async function approveAllSuggestions() {
  return request<ApiResponse<BatchSuggestionResult>>('/api/v1/ai/suggestions/approve-all', {
    method: 'POST',
  });
}

export async function batchRejectSuggestions(ids: number[]) {
  return request<ApiResponse<BatchSuggestionResult>>('/api/v1/ai/suggestions/batch-reject', {
    method: 'POST',
    data: { ids },
  });
}

export async function previewAiOrganization(params?: AiOrganizationRequest) {
  return request<ApiResponse<AiOrganizationResponse>>('/api/v1/ai/organization/preview', {
    method: 'GET',
    params,
  });
}

export async function applyAiOrganization(data: AiOrganizationRequest) {
  return request<ApiResponse<AiOrganizationStartResult>>('/api/v1/ai/organization/apply', {
    method: 'POST',
    data,
  });
}

export async function getAiOrganizationStatus() {
  return request<ApiResponse<AiOrganizationJobStatus>>('/api/v1/ai/organization/status', {
    method: 'GET',
  });
}

export async function cancelAiOrganization() {
  return request<ApiResponse<boolean>>('/api/v1/ai/organization/cancel', {
    method: 'POST',
  });
}

export function defaultLibraryAiConfig(providerId: string): string {
  if (providerId === 'openai-compatible') {
    return JSON.stringify(
      {
        openaiLlmBaseUrl: 'https://api.openai.com/v1',
        llmApiKey: '',
        openaiEmbedBaseUrl: 'https://api.openai.com/v1',
        embedApiKey: '',
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
