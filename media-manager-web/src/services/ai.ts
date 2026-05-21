import { request } from '@umijs/max';

export async function getAiHealth() {
  return request('/api/v1/ai/health', { method: 'GET' });
}

export async function listAiSuggestions() {
  return request('/api/v1/ai/suggestions', { method: 'GET' });
}

export async function approveSuggestion(id: number) {
  return request(`/api/v1/ai/suggestions/${id}/approve`, { method: 'POST' });
}

export async function rejectSuggestion(id: number) {
  return request(`/api/v1/ai/suggestions/${id}/reject`, { method: 'POST' });
}
