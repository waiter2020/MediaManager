import { request } from '@umijs/max';
import type { ApiResponse } from '@/types/api';

export interface ClassificationRule {
  id?: number;
  name: string;
  ruleType: 'PATH' | 'FILENAME' | 'REGEX' | string;
  expression: string;
  targetType: 'TAG' | 'CATEGORY' | string;
  targetValue: string;
  enabled?: boolean;
  priority?: number;
  createdAt?: string;
}

export async function listClassificationRules() {
  return request<ApiResponse<ClassificationRule[]>>('/api/v1/classification-rules', { method: 'GET' });
}

export async function createClassificationRule(data: ClassificationRule) {
  return request<ApiResponse<ClassificationRule>>('/api/v1/classification-rules', { method: 'POST', data });
}

export async function updateClassificationRule(id: number, data: ClassificationRule) {
  return request<ApiResponse<ClassificationRule>>(`/api/v1/classification-rules/${id}`, { method: 'PUT', data });
}

export async function deleteClassificationRule(id: number) {
  return request<ApiResponse<void>>(`/api/v1/classification-rules/${id}`, { method: 'DELETE' });
}
